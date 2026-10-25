package lkd.namsic.coredefender.wave;

import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.script.ScriptType;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.kubejs.CoreDefenderKubeEvents;
import lkd.namsic.coredefender.kubejs.WaveEndKubeEvent;
import lkd.namsic.coredefender.wall.WallGeometry;
import lkd.namsic.coredefender.wall.WallManager;
import lkd.namsic.coredefender.wall.WallSegment;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 진행 중인 웨이브 하나를 관리한다. 웨이브 상태는 저장하지 않으며, 서버 재시작 시 사라진다
 * (남은 웨이브 몹은 월드 로드 시 제거됨 — WaveEvents#onEntityJoin).
 */
@UtilityClass
public class WaveManager {
    /** 웨이브 중 스폰 범위를 로딩하는 티켓. 저장된 티켓은 월드 로드 시 모두 버린다. */
    public static final TicketController TICKETS = new TicketController(
            ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "wave"),
            (level, helper) -> List.copyOf(helper.getBlockTickets().keySet()).forEach(helper::removeAllTickets));

    private static final int CONTROLLER_INTERVAL = 4;
    private static final int SPAWN_ATTEMPTS = 10;

    @Nullable
    @Getter
    private static ActiveWave active;

    @RequiredArgsConstructor
    public static final class ActiveWave {
        @Getter
        private final WaveSpec spec;
        private final List<WaveSpec.MobEntry> queue;
        private final Map<UUID, WaveMobController.MobState> mobs = new HashMap<>();
        private final List<long[]> forcedChunks = new ArrayList<>();
        private final BlockPos ticketOwner;
        private final ServerBossEvent bossBar;
        /** 성벽 상태: 패배 조건이 "구간 하나라도 붕괴"이므로 가장 약한 구간 기준으로 표시 */
        private final ServerBossEvent wallBar;
        private int spawnTimer;

        @Nullable
        public WaveMobController.MobState mobState(UUID uuid) {
            return this.mobs.get(uuid);
        }

        public int remaining() {
            return this.queue.size() + this.mobs.size();
        }

        public boolean tracks(UUID uuid) {
            return this.mobs.containsKey(uuid);
        }
    }

    public static boolean isActive() {
        return active != null;
    }

    /** 웨이브 시작. 이미 진행 중이거나 붕괴한 성벽이 있으면 실패 사유를, 성공하면 null 을 돌려준다. */
    @Nullable
    public static String start(MinecraftServer server, WaveSpec spec) {
        ServerLevel level = WallManager.level(server);
        if (level == null) {
            return "defense dimension not loaded";
        }
        if (active != null) {
            return "wave already active";
        }
        WallManager.ensureBuilt(server);
        if (WallManager.anyCollapsed(level)) {
            return "wall collapsed";
        }

        List<WaveSpec.MobEntry> queue = new ArrayList<>();
        for (WaveSpec.MobEntry entry : spec.mobs()) {
            for (int i = 0; i < entry.count(); i++) {
                queue.add(new WaveSpec.MobEntry(entry.type(), 1, entry.elite(), entry.breaker()));
            }
        }
        Collections.shuffle(queue, new Random(level.getRandom().nextLong()));

        WallGeometry geo = WallManager.state(level).geometry();
        ServerBossEvent bar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        ServerBossEvent wallBar = new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        active = new ActiveWave(spec, queue, geo.corePos(), bar, wallBar);
        forceChunks(level, geo, WaveSpawnArea.margin(geo, spec.spawnMax()));
        updateBossBar(level);
        return null;
    }

    private static void forceChunks(ServerLevel level, WallGeometry geo, int margin) {
        if (active == null) return;
        int minCx = (geo.outerMinX() - margin) >> 4;
        int maxCx = (geo.outerMaxX() + margin) >> 4;
        int minCz = (geo.outerMinZ() - margin) >> 4;
        int maxCz = (geo.outerMaxZ() + margin) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                TICKETS.forceChunk(level, active.ticketOwner, cx, cz, true, true);
                active.forcedChunks.add(new long[]{cx, cz});
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (active == null) {
            return;
        }
        ServerLevel level = WallManager.level(server);
        if (level == null) {
            return;
        }
        WallGeometry geo = WallManager.state(level).geometry();
        long now = level.getGameTime();

        if (!active.queue.isEmpty() && --active.spawnTimer <= 0 && active.mobs.size() < active.spec.maxAlive()) {
            spawnNext(level, geo);
            active.spawnTimer = active.spec.spawnInterval();
        }

        FlowField field = FlowFieldManager.get(level, WaveSpawnArea.margin(geo, active.spec.spawnMax()));
        Iterator<Map.Entry<UUID, WaveMobController.MobState>> it = active.mobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, WaveMobController.MobState> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                it.remove();
                continue;
            }
            if ((now + mob.getId()) % CONTROLLER_INTERVAL == 0) {
                WaveMobController.tick(level, mob, entry.getValue(), geo, field);
                // A melee hit can collapse a wall and synchronously finish/clean up this wave.
                if (active == null) return;
            }
            WaveMobController.tickControls(mob, entry.getValue());
        }

        if (now % 10 == 0) {
            updateBossBar(level);
        }
        if (active != null && active.queue.isEmpty() && active.mobs.isEmpty()) {
            finish(server, true, "cleared");
        }
    }

    private static void spawnNext(ServerLevel level, WallGeometry geo) {
        if (active == null) return;
        WaveSpec.MobEntry entry = active.queue.removeFirst();
        EntityType<?> type = EntityType.byString(entry.type()).orElse(null);
        if (type == null) {
            CoreDefender.LOGGER.warn("Unknown wave mob type: {}", entry.type());
            return;
        }
        BlockPos pos = findSpawnPos(level, geo);
        if (pos == null || !(type.create(level) instanceof Mob mob)) {
            return;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        // 치킨 조키 등 탑승물은 웨이브 대상이 아니므로 제거
        if (mob.getVehicle() != null) {
            mob.getVehicle().discard();
        }
        WaveMobs.prepare(mob, entry.elite(), entry.breaker());
        active.mobs.put(mob.getUUID(), new WaveMobController.MobState());
        level.addFreshEntity(mob);
    }

    /** 기지 외접원 + [min, max] 간격의 원형 띠 전체에서 무작위 지점 */
    @Nullable
    private static BlockPos findSpawnPos(ServerLevel level, WallGeometry geo) {
        if (active == null) return null;
        RandomSource random = level.getRandom();
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            WaveSpawnArea.Point point = WaveSpawnArea.sample(geo, active.spec.spawnMin(), active.spec.spawnMax(),
                    random.nextDouble(), random.nextDouble());
            int x = (int) Math.floor(point.x());
            int z = (int) Math.floor(point.z());
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    private static void updateBossBar(ServerLevel level) {
        if (active == null) return;
        int total = Math.max(1, active.spec.total());
        int remaining = active.remaining();
        active.bossBar.setName(Component.translatable("bossbar.coredefender.wave", active.spec.label(), remaining));
        active.bossBar.setProgress(Math.clamp((float) remaining / total, 0.0F, 1.0F));

        String weakestId = "";
        WallSegment weakest = null;
        for (Map.Entry<String, WallSegment> entry : WallManager.state(level).segments().entrySet()) {
            WallSegment segment = entry.getValue();
            if (weakest == null || (double) segment.hp() / segment.maxHp() < (double) weakest.hp() / weakest.maxHp()) {
                weakest = segment;
                weakestId = entry.getKey();
            }
        }
        if (weakest != null) {
            float ratio = Math.clamp((float) weakest.hp() / weakest.maxHp(), 0.0F, 1.0F);
            active.wallBar.setName(Component.translatable("bossbar.coredefender.wall", weakestId, weakest.hp(), weakest.maxHp()));
            active.wallBar.setProgress(ratio);
            active.wallBar.setColor(ratio > 0.5F ? BossEvent.BossBarColor.GREEN : ratio > 0.25F ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
        }

        for (ServerBossEvent bar : List.of(active.bossBar, active.wallBar)) {
            for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
                // Minecraft owns this level; borrowers must not close it.
                //noinspection resource
                if (player.level() != level) {
                    bar.removePlayer(player);
                }
            }
            for (ServerPlayer player : level.players()) {
                bar.addPlayer(player);
            }
        }
    }

    /** 성벽 구간 붕괴 → 진행 중이면 패배 */
    public static void onWallCollapsed(MinecraftServer server) {
        if (active != null) {
            finish(server, false, "wall_collapsed");
        }
    }

    /** 관리자 중단: 보상/패배 이벤트 없이 정리만 한다. */
    public static void abort(MinecraftServer server) {
        TestMobs.clear(server);
        if (active != null) {
            cleanup(server);
        }
    }

    private static void finish(MinecraftServer server, boolean success, String reason) {
        if (active == null) return;
        WaveSpec spec = active.spec;
        cleanup(server);
        var handler = success ? CoreDefenderKubeEvents.WAVE_CLEARED : CoreDefenderKubeEvents.WAVE_FAILED;
        handler.post(ScriptType.SERVER, new WaveEndKubeEvent(server, spec.label(), success, reason));
    }

    private static void cleanup(MinecraftServer server) {
        ActiveWave wave = active;
        if (wave == null) return;
        active = null;
        ServerLevel level = WallManager.level(server);
        if (level != null) {
            for (UUID uuid : wave.mobs.keySet()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null) {
                    entity.discard();
                }
            }
            for (long[] chunk : wave.forcedChunks) {
                TICKETS.forceChunk(level, wave.ticketOwner, (int) chunk[0], (int) chunk[1], false, true);
            }
        }
        wave.bossBar.removeAllPlayers();
        wave.wallBar.removeAllPlayers();
    }

    public static JsonObject info() {
        JsonObject json = new JsonObject();
        json.addProperty("active", active != null);
        if (active != null) {
            json.addProperty("label", active.spec.label());
            json.addProperty("remaining", active.remaining());
            json.addProperty("total", active.spec.total());
        }
        return json;
    }
}
