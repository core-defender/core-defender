package lkd.namsic.coredefender.wall;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.script.ScriptType;
import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.block.WallBlock;
import lkd.namsic.coredefender.kubejs.CoreDefenderKubeEvents;
import lkd.namsic.coredefender.kubejs.WallCollapsedKubeEvent;
import lkd.namsic.coredefender.registry.ModBlocks;
import lkd.namsic.coredefender.wave.FlowFieldManager;
import lkd.namsic.coredefender.wave.WaveManager;
import lombok.experimental.UtilityClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 성벽 생성/피해/수복/확장. 모든 메서드는 서버 스레드에서 호출한다.
 */
@UtilityClass
public class WallManager {
    public static final int INITIAL_HALF = 1;
    public static final int BASE_SEGMENT_HP = 500;
    /** 강화 1단계당 최대 HP 증가율 */
    public static final double HP_PER_LEVEL = 0.5;
    /** 확장 시 기존 성벽 윗면부터 이 높이까지의 블록을 회수 */
    private static final int ABOVE_WALL_CLEAR_HEIGHT = 32;

    @Nullable
    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(DefenseDimension.KEY);
    }

    public static WallState state(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WallState.FACTORY, WallState.ID);
    }

    /** 성벽/코어가 아직 없으면 차원 중앙(0, 0)에 생성한다. */
    public static void ensureBuilt(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null) {
            return;
        }
        WallState state = state(level);
        if (state.built) {
            return;
        }
        state.centerX = 0;
        state.centerZ = 0;
        // 청크가 로드되지 않았으면 getHeight 가 최저 높이를 돌려주므로 먼저 생성/로드한다
        level.getChunk(state.centerX >> 4, state.centerZ >> 4);
        state.baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, state.centerX, state.centerZ);
        state.half = INITIAL_HALF;
        state.resetSegments();
        placeWall(level, state);
        level.setBlock(state.geometry().corePos(), ModBlocks.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        state.built = true;
        state.setDirty();
    }

    // ---------- 블록 배치 ----------

    private static void placeWall(ServerLevel level, WallState state) {
        WallGeometry geo = state.geometry();
        for (int[] column : geo.columns()) {
            WallSegment segment = state.segments.get(geo.segmentAt(column[0], column[1]));
            BlockState block = ModBlocks.WALL.get().defaultBlockState()
                    .setValue(WallBlock.BROKEN, segment != null && segment.collapsed);
            for (int y = geo.baseY(); y < geo.baseY() + geo.height(); y++) {
                level.setBlock(new BlockPos(column[0], y, column[1]), block, Block.UPDATE_ALL);
            }
        }
    }

    private static void removeWall(ServerLevel level, WallState state) {
        WallGeometry geo = state.geometry();
        for (String id : geo.segmentIds()) {
            sendCracks(level, geo, id, 0);
        }
        for (int[] column : geo.columns()) {
            for (int y = geo.baseY(); y < geo.baseY() + geo.height(); y++) {
                level.setBlock(new BlockPos(column[0], y, column[1]), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void setSegmentBroken(ServerLevel level, WallGeometry geo, String id, boolean broken) {
        BlockState block = ModBlocks.WALL.get().defaultBlockState().setValue(WallBlock.BROKEN, broken);
        for (BlockPos pos : geo.segmentBlocks(id)) {
            level.setBlock(pos, block, Block.UPDATE_ALL);
        }
    }

    // ---------- 균열 표시 ----------

    private static int crackId(BlockPos pos) {
        // 실제 엔티티 ID(양수)와 겹치지 않도록 음수 사용
        return -1 - (Long.hashCode(pos.asLong()) & 0x3FFFFFFF);
    }

    /** stage 0 이면 균열 제거 */
    private static void sendCracks(ServerLevel level, WallGeometry geo, String id, int stage) {
        for (BlockPos pos : geo.segmentOuterFace(id)) {
            level.destroyBlockProgress(crackId(pos), pos, stage > 0 ? stage : -1);
        }
    }

    /** 클라이언트는 갱신 없는 균열을 약 20초 뒤 지우므로 주기적으로 다시 보낸다. */
    public static void refreshCracks(MinecraftServer server) {
        ServerLevel level = level(server);
        if (level == null || level.players().isEmpty()) {
            return;
        }
        WallState state = state(level);
        WallGeometry geo = state.geometry();
        state.segments.forEach((id, segment) -> {
            int stage = segment.crackStage();
            if (stage > 0) {
                sendCracks(level, geo, id, stage);
            }
        });
    }

    // ---------- 피해 / 수복 / 확장 ----------

    /** 성벽 블록 위치로 피해. 성벽이 아니면 false. */
    public static boolean damageAt(ServerLevel level, BlockPos pos, int amount) {
        WallState state = state(level);
        if (!state.built || !state.geometry().isWallBlock(pos)) {
            return false;
        }
        damage(level, state.geometry().segmentAt(pos.getX(), pos.getZ()), amount);
        hitEffect(level, pos);
        DamageNumbers.spawn(level, Vec3.atCenterOf(pos), amount, ChatFormatting.RED);
        return true;
    }

    /** 맞은 블록에서 파편과 타격음 */
    private static void hitEffect(ServerLevel level, BlockPos pos) {
        BlockState block = level.getBlockState(pos);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, block),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.4, 0.4, 0.4, 0.05);
        SoundType sound = block.getSoundType(level, pos, null);
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, sound.getVolume(), sound.getPitch() * 0.8F);
    }

    public static void damage(ServerLevel level, String id, int amount) {
        WallState state = state(level);
        WallSegment segment = state.segments.get(id);
        if (segment == null || segment.collapsed || amount <= 0) {
            return;
        }
        int before = segment.crackStage();
        segment.hp = Math.max(0, segment.hp - amount);
        state.setDirty();

        if (segment.hp == 0) {
            collapse(level, state, id);
        } else if (segment.crackStage() != before) {
            sendCracks(level, state.geometry(), id, segment.crackStage());
        }
    }

    private static void collapse(ServerLevel level, WallState state, String id) {
        state.segments.get(id).collapsed = true;
        state.setDirty();
        WallGeometry geo = state.geometry();
        sendCracks(level, geo, id, 0);
        setSegmentBroken(level, geo, id, true);

        MinecraftServer server = level.getServer();
        WaveManager.onWallCollapsed(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.coredefender.wall_collapsed", id).withStyle(ChatFormatting.RED), false);
        CoreDefenderKubeEvents.WALL_COLLAPSED.post(ScriptType.SERVER, new WallCollapsedKubeEvent(server, id));
    }

    /** 구간 수복. id 가 null 이면 전체. amount 가 0 이하면 최대 HP 까지. */
    public static void repair(ServerLevel level, @Nullable String id, int amount) {
        WallState state = state(level);
        WallGeometry geo = state.geometry();
        for (Map.Entry<String, WallSegment> entry : state.segments.entrySet()) {
            if (id != null && !id.equals(entry.getKey())) {
                continue;
            }
            WallSegment segment = entry.getValue();
            boolean wasCollapsed = segment.collapsed;
            segment.hp = amount > 0 ? Math.min(segment.maxHp, segment.hp + amount) : segment.maxHp;
            segment.collapsed = false;
            if (wasCollapsed) {
                setSegmentBroken(level, geo, entry.getKey(), false);
            }
            sendCracks(level, geo, entry.getKey(), segment.crackStage());
        }
        state.setDirty();
    }

    public static int segmentMaxHp(int sideLevel) {
        return (int) Math.round(BASE_SEGMENT_HP * (1 + HP_PER_LEVEL * sideLevel));
    }

    /** 한 변을 강화: 해당 변 구간들의 최대 HP 와 현재 HP 가 함께 오른다. */
    public static void upgradeSide(ServerLevel level, String side) {
        WallState state = state(level);
        int index = WallState.sideIndex(side);
        state.sideLevels[index]++;
        int newMax = segmentMaxHp(state.sideLevels[index]);
        WallGeometry geo = state.geometry();
        state.segments.forEach((id, segment) -> {
            if (id.startsWith(side)) {
                if (!segment.collapsed) {
                    segment.hp += newMax - segment.maxHp;
                }
                segment.maxHp = newMax;
                sendCracks(level, geo, id, segment.crackStage());
            }
        });
        state.setDirty();
    }

    public static boolean anyCollapsed(ServerLevel level) {
        return state(level).segments.values().stream().anyMatch(s -> s.collapsed);
    }

    /** 한 단계 확장: 기존 성벽을 철거하고 내부를 사방 1청크씩 넓힌 새 성벽을 세운다. */
    public static void expand(ServerLevel level) {
        WallState state = state(level);
        WallGeometry oldGeo = state.geometry();
        // 1. 기존 성벽 위에 지은 구조물(포탑 등)은 코어 창고로 회수
        for (int[] column : oldGeo.columns()) {
            for (int y = oldGeo.topY(); y < oldGeo.topY() + ABOVE_WALL_CLEAR_HEIGHT; y++) {
                BlockPos pos = new BlockPos(column[0], y, column[1]);
                if (!level.getBlockState(pos).isAir()) {
                    CoreStorage.salvage(level, pos);
                }
            }
        }
        removeWall(level, state);
        state.half++;

        // 2. 새 성벽 범위 안으로 들어온 킬존 장애물은 코어 창고로 회수 (성벽 안은 기지, 성벽 자리는 덮어씀)
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

    /** 현재 상태 그대로 성벽/코어 블록을 다시 배치 (맵 손상 복구용) */
    public static void rebuild(ServerLevel level) {
        WallState state = state(level);
        placeWall(level, state);
        level.setBlock(state.geometry().corePos(), ModBlocks.CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    // ---------- 조회 ----------

    public static JsonObject info(ServerLevel level) {
        WallState state = state(level);
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
        json.addProperty("segmentsPerSide", geo.segmentsPerSide());
        BlockPos core = geo.corePos();
        json.addProperty("coreX", core.getX());
        json.addProperty("coreY", core.getY());
        json.addProperty("coreZ", core.getZ());
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
}
