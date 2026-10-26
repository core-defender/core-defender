package lkd.namsic.coredefender.wall;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.wave.WaveMobs;
import lkd.namsic.coredefender.wave.WaveProjectiles;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;

@EventBusSubscriber(modid = CoreDefender.MOD_ID)
public final class BarrierSystem {
    private BarrierSystem() {
    }

    public static Vec3 clipMovement(Entity entity, Vec3 motion) {
        // Minecraft owns this level; borrowers must not close it.
        //noinspection resource
        if (!(entity instanceof Mob) || !WaveMobs.isWaveMob(entity) || motion.lengthSqr() == 0
            || !(entity.level() instanceof ServerLevel level) || !DefenseDimension.is(level)) {
            return motion;
        }

        WallState state = WallManager.state(level);
        if (!state.built()) {
            return motion;
        }

        AABB swept = entity.getBoundingBox().expandTowards(motion).inflate(0.001);
        WallGeometry geo = state.geometry();
        if (swept.maxX < geo.outerMinX() - 0.1 || swept.minX > geo.outerMaxX() + 1.1
            || swept.maxZ < geo.outerMinZ() - 0.1 || swept.minZ > geo.outerMaxZ() + 1.1
            || swept.maxY < geo.baseY() || swept.minY > geo.topY()) {
            return motion;
        }

        ArrayList<VoxelShape> shapes = new ArrayList<>(4);
        for (BarrierGeometry.Panel panel : state.panels()) {
            if (state.active(panel.id()) && panel.box().intersects(swept)) {
                shapes.add(panel.shape());
            }
        }

        return shapes.isEmpty() ? motion : BarrierGeometry.clipMovement(entity.getBoundingBox(), motion, shapes);
    }

    public record Impact(String segment, Vec3 point) {
    }

    public static BarrierGeometry.@Nullable Panel nearestActive(ServerLevel level, Vec3 point) {
        WallState state = WallManager.state(level);
        BarrierGeometry.Panel result = null;
        double nearest = Double.MAX_VALUE;
        for (BarrierGeometry.Panel panel : state.panels()) {
            double distance = panel.nearest(point).distanceToSqr(point);
            if (state.active(panel.id()) && distance < nearest) {
                result = panel;
                nearest = distance;
            }
        }

        return result;
    }

    public static @Nullable Impact trace(WallState state, Vec3 from, Vec3 to) {
        Impact result = null;
        double nearest = Double.MAX_VALUE;
        for (BarrierGeometry.Panel panel : state.panels()) {
            if (!state.active(panel.id())) {
                continue;
            }

            Optional<Vec3> intersection = panel.box().clip(from, to);
            Vec3 point = panel.box().contains(from) ? from : intersection.orElse(null);
            if (point != null && from.distanceToSqr(point) < nearest) {
                nearest = from.distanceToSqr(point);
                result = new Impact(panel.id(), point);
            }
        }

        return result;
    }

    /**
     * 기존 근접 경로 탐색에는 장벽이 보이지 않으므로, 장벽 너머 플레이어를 도달 가능으로 보지 않는다.
     */
    public static boolean separates(Mob mob, Entity target) {
        // Minecraft owns this level; borrowers must not close it.
        //noinspection resource
        return mob.level() instanceof ServerLevel level && DefenseDimension.is(level)
            && trace(WallManager.state(level), mob.getBoundingBox().getCenter(), target.getBoundingBox().getCenter()) != null;
    }

    @SubscribeEvent
    public static void beforeProjectileTick(EntityTickEvent.Pre event) {
        // Minecraft owns this level; borrowers must not close it.
        //noinspection resource
        if (!(event.getEntity() instanceof Projectile projectile) || !WaveProjectiles.isHostile(projectile)
            || !(projectile.level() instanceof ServerLevel level) || !DefenseDimension.is(level)) {
            return;
        }

        if (interceptProjectile(level, projectile)) {
            event.setCanceled(true);
        }
    }

    static boolean interceptProjectile(ServerLevel level, Projectile projectile) {
        if (!WaveProjectiles.isHostile(projectile)) {
            return false;
        }

        Vec3 from = projectile.position(), to = from.add(projectile.getDeltaMovement());
        Impact impact = trace(WallManager.state(level), from, to);
        if (impact == null) {
            return false;
        }

        // 장벽보다 먼저 만나는 일반 블록/비아군 엔티티는 원래 투사체 처리가 담당한다.
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile));
        double distance = from.distanceToSqr(impact.point());
        if (block.getType() != HitResult.Type.MISS && from.distanceToSqr(block.getLocation()) < distance) {
            return false;
        }

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, projectile, from, impact.point(),
            projectile.getBoundingBox().expandTowards(projectile.getDeltaMovement()).inflate(1),
            entity -> entity != projectile.getOwner() && entity.isPickable() && !entity.isSpectator() && !WaveMobs.isWaveMob(entity));
        if (entityHit != null && from.distanceToSqr(entityHit.getLocation()) < distance) {
            return false;
        }

        WallManager.hit(level, impact.segment(), impact.point(), (int) Math.round(Balance.RANGED_WALL_DAMAGE * WaveProjectiles.damageScale(projectile)));
        projectile.discard();
        return true;
    }
}
