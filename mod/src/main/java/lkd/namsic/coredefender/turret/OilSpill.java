package lkd.namsic.coredefender.turret;

import lkd.namsic.coredefender.wall.WallGeometry;
import lkd.namsic.coredefender.network.OilSurfacePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transient surface fire, bounded per turret and never backed by fluid or fire blocks. */
public final class OilSpill {
    private static final int MAX_CELLS = OilSurfacePayload.MAX_CELLS;
    private final Map<BlockPos, Cell> cells = new LinkedHashMap<>();
    private long cycleStarted = -1;
    private boolean visible;

    private record Pour(TurretStats stats, int amplifier, long expires) {
    }

    private record Cell(BlockPos block, double y, Pour pour, int distance) {
        Vec3 center() {
            return new Vec3(this.block.getX() + .5, this.y, this.block.getZ() + .5);
        }
    }

    public static @Nullable Vec3 outlet(ServerLevel world, WallGeometry wall, BlockPos turret, float yaw) {
        Direction direction = Direction.fromYRot(yaw);
        BlockPos outside = turret.relative(direction);
        if (!wall.isInterior(turret.getX(), turret.getZ()) || wall.isInterior(outside.getX(), outside.getZ())) {
            return null;
        }

        return surface(world, wall, outside.getX(), outside.getZ(), turret.getY() + 1.3);
    }

    private static @Nullable Vec3 surface(ServerLevel world, WallGeometry wall, int x, int z, double ceiling) {
        if (wall.isInterior(x, z)) {
            return null;
        }

        for (int y = (int) Math.floor(ceiling); y >= Math.max(world.getMinBuildHeight(), Math.floor(ceiling) - 8); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!world.getChunkSource().hasChunk(x >> 4, z >> 4) || !world.getFluidState(pos).isEmpty()) {
                return null;
            }

            VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);
            if (!shape.isEmpty()) {
                double top = y + shape.max(Direction.Axis.Y);
                return top <= ceiling + .001 ? new Vec3(x + .5, top, z + .5) : null;
            }
        }

        return null;
    }

    public void pour(Vec3 position, long now, TurretStats stats, int amplifier) {
        BlockPos key = BlockPos.containing(position.add(0, -.001, 0));
        if (this.cells.size() < MAX_CELLS || this.cells.containsKey(key)) {
            this.cells.put(key, new Cell(key, position.y, new Pour(stats, amplifier, now + TurretStats.OIL_LIFETIME), 0));
            if (this.cycleStarted < 0) {
                this.cycleStarted = now + 1;
            }
        }
    }

    public void tick(ServerLevel world, WallGeometry wall, BasicTurretBlockEntity turret) {
        long now = world.getGameTime();
        if (now % 5 == 0) {
            this.cells.values().removeIf(cell -> {
                Vec3 surface = surface(world, wall, cell.block.getX(), cell.block.getZ(), cell.y);
                return cell.pour.expires <= now || surface == null || Math.abs(surface.y - cell.y) > .01;
            });
        }

        if (now % 10 == 0) {
            for (Cell cell : List.copyOf(this.cells.values())) {
                this.spread(world, wall, cell);
            }
        }

        if (now % 5 == 0 && (!this.cells.isEmpty() || this.visible)) {
            List<OilSurfacePayload.Cell> snapshot = this.cells.values().stream()
                .map(cell -> new OilSurfacePayload.Cell(cell.block, (float) (cell.y - cell.block.getY()), (int) Math.max(0, cell.pour.expires - now)))
                .toList();
            PacketDistributor.sendToPlayersTrackingChunk(world, new ChunkPos(turret.getBlockPos()),
                new OilSurfacePayload(world.dimension().location(), turret.getBlockPos(), turret.identity, snapshot));
            this.visible = !snapshot.isEmpty();
        }

        if (this.cells.isEmpty()) {
            this.cycleStarted = -1;
            return;
        }

        // All cells share one firing clock so overlapping pours cannot multiply the four-hit burst.
        long phase = (now - this.cycleStarted) % turret.stats().interval(0);
        boolean hit = phase == 0 || phase == 3 || phase == 6 || phase == 9;
        if (now % 5 != 0 && !hit) {
            return;
        }

        AABB bounds = new AABB(this.cells.values().iterator().next().block);
        int stride = Math.max(1, (this.cells.size() + 11) / 12);
        int index = 0;
        for (Cell cell : this.cells.values()) {
            // Rotate a bounded sample so large patches do not multiply multiplayer particle packets.
            if (now % 5 == 0 && index++ % stride == (now / 5) % stride) {
                world.sendParticles(ParticleTypes.SMOKE, cell.block.getX() + .5, cell.y + .08, cell.block.getZ() + .5, 1, .3, .02, .3, .005);
                world.sendParticles(ParticleTypes.FLAME, cell.block.getX() + .5, cell.y + .12, cell.block.getZ() + .5, 1, .3, .02, .3, .005);
            }

            AABB area = new AABB(cell.block.getX(), cell.y, cell.block.getZ(), cell.block.getX() + 1, cell.y + .6, cell.block.getZ() + 1);
            bounds = bounds.minmax(area);
        }

        if (!hit) {
            return;
        }

        for (Mob mob : world.getEntitiesOfClass(Mob.class, bounds, TurretCombat::isTarget)) {
            if (TurretCombat.fireImmune(mob) || wall.isInterior(mob.getBlockX(), mob.getBlockZ())) {
                continue;
            }

            double damage = 0;
            double burn = 0;
            for (Cell cell : this.cells.values()) {
                if (cell.pour.expires <= now || wall.isInterior(cell.block.getX(), cell.block.getZ())) {
                    continue;
                }

                AABB contact = new AABB(cell.block.getX(), cell.y - .05, cell.block.getZ(), cell.block.getX() + 1, cell.y + .4, cell.block.getZ() + 1);
                if (mob.getBoundingBox().intersects(contact)) {
                    double candidate = cell.pour.stats.damage(false, cell.pour.amplifier);
                    double candidateBurn = cell.pour.stats.burn(cell.pour.amplifier);
                    if (candidate > damage || candidate == damage && candidateBurn > burn) {
                        damage = candidate;
                        burn = candidateBurn;
                    }
                }
            }

            if (damage > 0) {
                TurretCombat.damage(world, mob, TurretCombat.fireSource(world), damage, turret.getBlockPos(), turret.identity, null);
                TurretCombat.burn(mob, turret, burn);
            }
        }
    }

    private void spread(ServerLevel world, WallGeometry wall, Cell cell) {
        if (this.cells.size() >= MAX_CELLS) {
            return;
        }

        List<Vec3> candidates = flowCandidates(world, wall, cell.center());
        int remaining = (int) Math.floor(cell.pour.stats.range(false, cell.pour.amplifier)) - cell.distance;
        int bestSlope = Integer.MAX_VALUE;
        Map<Vec3, Integer> slopes = new LinkedHashMap<>();
        for (Vec3 next : candidates) {
            if (next.y >= cell.y - .01 && remaining <= 0) {
                continue;
            }

            int slope = next.y < cell.y - .01 ? 0
                : slopeDistance(world, wall, next, cell.center(), Math.min(3, remaining - 1));
            slopes.put(next, slope);
            bestSlope = Math.min(bestSlope, slope);
        }

        for (Map.Entry<Vec3, Integer> entry : slopes.entrySet()) {
            if (entry.getValue() != bestSlope || this.cells.size() >= MAX_CELLS) {
                continue;
            }

            Vec3 next = entry.getKey();
            int distance = next.y < cell.y - .01 ? 0 : cell.distance + 1;
            BlockPos key = BlockPos.containing(next.add(0, -.001, 0));
            Cell old = this.cells.get(key);
            // Falling renews horizontal reach, but never the lifetime of the original pour.
            if (old == null || old.pour.expires < cell.pour.expires
                || old.pour.expires == cell.pour.expires && distance < old.distance) {
                this.cells.put(key, new Cell(key, next.y, cell.pour, distance));
            }
        }
    }

    static List<Vec3> flowCandidates(ServerLevel world, WallGeometry wall, Vec3 origin) {
        List<Vec3> candidates = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Vec3 surface = surface(world, wall, (int) Math.floor(origin.x) + direction.getStepX(),
                (int) Math.floor(origin.z) + direction.getStepZ(), origin.y);
            if (surface == null) {
                continue;
            }

            Vec3 from = origin.add(0, .05, 0);
            Vec3 across = new Vec3(surface.x, origin.y + .05, surface.z);
            if (world.clip(new ClipContext(from, across, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty())).getType() == HitResult.Type.MISS) {
                candidates.add(surface);
            }
        }

        return candidates;
    }

    static int slopeDistance(ServerLevel world, WallGeometry wall, Vec3 from, Vec3 previous, int remaining) {
        if (remaining <= 0) {
            return Integer.MAX_VALUE;
        }

        int best = Integer.MAX_VALUE;
        for (Vec3 next : flowCandidates(world, wall, from)) {
            if (next.x == previous.x && next.z == previous.z) {
                continue;
            }

            if (next.y < from.y - .01) {
                return 1;
            }

            int distance = slopeDistance(world, wall, next, from, remaining - 1);
            if (distance != Integer.MAX_VALUE) {
                best = Math.min(best, distance + 1);
            }
        }

        return best;
    }
}
