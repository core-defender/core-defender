package lkd.namsic.coredefender.wall;

import net.minecraft.server.level.ServerPlayer;

import lkd.namsic.coredefender.supply.SupplyBlockEntity;
import net.minecraft.sounds.SoundEvents;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.script.ScriptType;
import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.kubejs.CoreDefenderKubeEvents;
import lkd.namsic.coredefender.kubejs.WallCollapsedKubeEvent;
import lkd.namsic.coredefender.registry.ModBlocks;
import lkd.namsic.coredefender.wave.FlowFieldManager;
import lkd.namsic.coredefender.wave.WaveManager;
import lombok.experimental.UtilityClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Arrays;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.killzone.KillzoneManager;

/**
 * 성벽 생성/피해/수복/확장. 모든 메서드는 서버 스레드에서 호출한다.
 */
@UtilityClass
public class WallManager {
    public static final int INITIAL_HALF = 1;
    public static final int BASE_SEGMENT_HP = 500;
    /**
     * 강화 1단계당 최대 HP 증가율
     */
    public static final double HP_PER_LEVEL = 0.5;

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DefenseDimension.KEY);
    }

    public static WallState state(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WallState.FACTORY, WallState.ID);
    }

    /**
     * 성벽/코어가 아직 없으면 차원 중앙(0, 0)에 생성한다.
     */
    public static void ensureBuilt(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) {
            return;
        }

        WallState state = state(level);
        if (state.built) {
            migrateBarrier(level, state);
            // 블록 엔티티가 생기기 전에 설치된 코어는 서버 스레드 조회 시 엔티티가 생성된다.
            level.getBlockEntity(state.geometry().corePos());
            // 기존 블록 성벽 두께는 이전 처리에만 사용한다. 장벽 경계는 정확히 청크 크기다.
            if (state.thickness != 0) {
                state.thickness = 0;
                state.setDirty();
                FlowFieldManager.invalidate();
            }

            return;
        }

        state.centerX = 0;
        state.centerZ = 0;
        // 청크가 로드되지 않았으면 getHeight 가 최저 높이를 돌려주므로 먼저 생성/로드한다
        level.getChunk(0, 0);
        state.baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, state.centerX, state.centerZ);
        state.half = INITIAL_HALF;
        state.thickness = 0;
        state.resetSegments();
        placeWall(level, state);
        level.setBlock(state.geometry().corePos(), ModBlocks.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        state.built = true;
        state.setDirty();
    }


    /**
     * 구 블록 성벽을 한 번만 전환. 상태/HP는 보존하고 성벽 위 시설만 회수한다.
     */
    private static void migrateBarrier(ServerLevel level, WallState state) {
        if (state.barrierMigrated) {
            return;
        }

        WallGeometry geo = state.geometry();
        for (int[] column : geo.columns()) {
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, column[0], column[1]);
            for (int y = geo.topY(); y < top; y++) {
                CoreStorage.salvage(level, new BlockPos(column[0], y, column[1]));
            }

            for (int y = geo.baseY(); y < geo.topY(); y++) {
                BlockPos pos = new BlockPos(column[0], y, column[1]);
                if (level.getBlockState(pos).is(ModBlocks.WALL.get())) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }

        state.barrierMigrated = true;
        state.setDirty();
        FlowFieldManager.invalidate();
    }

    private static void placeWall(ServerLevel level, WallState state) {
        // 장벽은 저장된 경계 데이터와 클라이언트 렌더링으로 존재한다.
        migrateBarrier(level, state);
    }

    public static void syncState(MinecraftServer server) {
        BarrierSync.tick(server);
    }


    public static void hit(ServerLevel level, String id, Vec3 point, double baseDamage) {
        int amount = WallDamage.scale(baseDamage, level.getDifficulty());
        if (amount == 0 || !state(level).active(id)) {
            return;
        }

        damage(level, id, amount);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 4, 0.2, 0.2, 0.2, 0.02);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.AMETHYST_BLOCK_HIT,
            SoundSource.BLOCKS, 0.4F, 1.5F);
        DamageNumbers.spawn(level, point, amount, ChatFormatting.RED);
    }

    public static void damage(ServerLevel level, String id, int amount) {
        WallState state = state(level);
        WallSegment segment = state.segments.get(id);
        if (segment == null || segment.collapsed || amount <= 0) {
            return;
        }

        segment.hp = Math.max(0, segment.hp - amount);
        state.setDirty();

        if (segment.hp == 0) {
            collapse(level, state, id);
        }
    }

    private static void collapse(ServerLevel level, WallState state, String id) {
        WallSegment segment = state.segments.get(id);
        if (segment == null) {
            return;
        }

        segment.collapsed = true;
        state.setDirty();

        MinecraftServer server = level.getServer();
        server.getPlayerList().broadcastSystemMessage(
            Component.translatable("message.coredefender.wall_collapsed", id).withStyle(ChatFormatting.RED), false);
        // 실패 이벤트가 수복 안내를 출력하므로 붕괴 알림 뒤에 호출한다.
        WaveManager.onWallCollapsed(server);
        CoreDefenderKubeEvents.WALL_COLLAPSED.post(ScriptType.SERVER, new WallCollapsedKubeEvent(server, id));
    }

    /**
     * 구간 수복. id 가 null 이면 전체. amount 가 0 이하면 최대 HP 까지.
     */
    public static void repair(ServerLevel level, @Nullable String id, int amount) {
        WallState state = state(level);
        for (Map.Entry<String, WallSegment> entry : state.segments.entrySet()) {
            if (id != null && !id.equals(entry.getKey())) {
                continue;
            }

            WallSegment segment = entry.getValue();
            segment.hp = amount > 0 ? (int) Math.min(segment.maxHp, (long) segment.hp + amount) : segment.maxHp;
            segment.collapsed = false;

        }

        state.setDirty();
    }

    public static int segmentMaxHp(int sideLevel) {
        return (int) Math.round(BASE_SEGMENT_HP * (1 + HP_PER_LEVEL * sideLevel));
    }

    /**
     * 한 변을 강화: 해당 변 구간들의 최대 HP 와 현재 HP 가 함께 오른다.
     */
    public static void upgradeSide(ServerLevel level, String side) {
        WallState state = state(level);
        int index = WallState.sideIndex(side);
        state.sideLevels[index]++;
        int newMax = segmentMaxHp(state.sideLevels[index]);
        state.segments.forEach((id, segment) -> {
            if (id.startsWith(side)) {
                if (!segment.collapsed) {
                    segment.hp += newMax - segment.maxHp;
                }

                segment.maxHp = newMax;
            }
        });
        state.setDirty();
    }

    public static boolean anyCollapsed(ServerLevel level) {
        return state(level).segments.values().stream().anyMatch(s -> s.collapsed);
    }

    /**
     * 경계를 사방 1청크씩 확장한다. 기존 일반 시설은 유지한다.
     */
    public static void expand(ServerLevel level) {
        WallState state = state(level);
        WallGeometry oldGeo = state.geometry();
        state.half++;
        state.largestHalf = Math.max(state.largestHalf, state.half);

        // 새 내부에 들어온 킬존 장애물은 코어 창고로 회수한다.
        WallGeometry newGeo = state.geometry();
        for (int x = newGeo.outerMinX(); x <= newGeo.outerMaxX(); x++) {
            for (int z = newGeo.outerMinZ(); z <= newGeo.outerMaxZ(); z++) {
                if (oldGeo.isInterior(x, z)) {
                    continue;
                }

                for (int y = newGeo.baseY(); y < newGeo.baseY() + 3; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState block = level.getBlockState(pos);
                    if (block.is(ModBlocks.BARRICADE.get()) || block.is(ModBlocks.SPIKE_TRAP.get())) {
                        CoreStorage.salvage(level, pos);
                    }
                }
            }
        }

        state.resetSegments();
        placeWall(level, state);
        FlowFieldManager.invalidate();
        state.setDirty();
    }

    /**
     * 경계 상태는 유지하고 코어 블록을 복구한다.
     */
    public static void rebuild(ServerLevel level) {
        WallState state = state(level);
        placeWall(level, state);
        level.setBlock(state.geometry().corePos(), ModBlocks.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    }


    /**
     * 크기/강화/HP를 선택 초기화. 장벽에는 실체 블록이 없어 일반 시설은 유지한다.
     */
    public static void reset(ServerLevel level, String scope) {
        WallState state = state(level);
        if (scope.equals("hp")) {
            repair(level, null, 0);
            return;
        }

        boolean resize = scope.equals("wall") || scope.equals("size");
        if (resize) {
            state.half = INITIAL_HALF;
            state.height = 5;
            state.thickness = 0;
        }

        if (scope.equals("wall") || scope.equals("levels")) {
            Arrays.fill(state.sideLevels, 0);
        }

        state.resetSegments();
        rebuild(level);
        if (resize) {
            BlockPos core = state.geometry().corePos();
            for (ServerPlayer player : level.players()) {
                player.teleportTo(level, core.getX() + 0.5, core.getY(), core.getZ() + 3.5, player.getYRot(), player.getXRot());
            }
        }

        FlowFieldManager.invalidate();
        state.setDirty();
    }

    /**
     * 현재 기지와 킬존의 지상 시설을 드롭 없이 삭제. 지형/코어/성벽은 유지.
     */
    public static void clearFacilities(ServerLevel level) {
        WallState state = state(level);
        WallGeometry geo = new WallGeometry(state.centerX, state.centerZ, state.baseY,
            Math.max(state.half, state.largestHalf), state.height, state.thickness);
        int margin = Balance.KILLZONE_WIDTH;
        for (int x = geo.outerMinX() - margin; x <= geo.outerMaxX() + margin; x++) {
            for (int z = geo.outerMinZ() - margin; z <= geo.outerMaxZ() + margin; z++) {
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = geo.baseY(); y < top; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(geo.corePos()) || level.getBlockState(pos).isAir()) {
                        continue;
                    }

                    KillzoneManager.unregister(level, pos);
                    if (level.getBlockEntity(pos) instanceof SupplyBlockEntity supply) {
                        supply.releaseChunks();
                    }

                    level.removeBlockEntity(pos);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }

        state.largestHalf = state.half;
        state.setDirty();
        FlowFieldManager.invalidate();
    }

    public static JsonObject info(ServerLevel level) {
        WallState state = state(level);
        JsonObject json = geometryInfo(state);
        JsonArray sides = new JsonArray();
        for (String side : WallGeometry.SIDES) {
            JsonObject s = new JsonObject();
            s.addProperty("side", side);
            s.addProperty("level", state.sideLevel(side));
            sides.add(s);
        }

        json.add("sides", sides);
        JsonArray segments = new JsonArray();
        state.segments.forEach((id, segment) -> {
            JsonObject s = new JsonObject();
            s.addProperty("id", id);
            s.addProperty("hp", segment.hp);
            s.addProperty("maxHp", segment.maxHp);
            s.addProperty("collapsed", segment.collapsed);
            segments.add(s);
        });
        json.add("segments", segments);
        return json;
    }

    private static JsonObject geometryInfo(WallState state) {
        WallGeometry geo = state.geometry();
        JsonObject json = new JsonObject();
        json.addProperty("built", state.built);
        json.addProperty("interiorChunks", geo.half() * 2);
        json.addProperty("minX", geo.minX());
        json.addProperty("maxX", geo.maxX());
        json.addProperty("minZ", geo.minZ());
        json.addProperty("maxZ", geo.maxZ());
        json.addProperty("baseY", geo.baseY());
        json.addProperty("height", geo.height());
        json.addProperty("thickness", geo.thickness());
        json.addProperty("barrier", true);
        json.addProperty("segmentsPerSide", geo.segmentsPerSide());
        BlockPos core = geo.corePos();
        json.addProperty("coreX", core.getX());
        json.addProperty("coreY", core.getY());
        json.addProperty("coreZ", core.getZ());
        return json;
    }
}
