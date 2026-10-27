package lkd.namsic.coredefender.wave;

import org.jspecify.annotations.Nullable;

import lkd.namsic.coredefender.wall.WallGeometry;
import net.minecraft.world.entity.Entity;

import lkd.namsic.coredefender.wall.WallManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import lombok.experimental.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 실제 웨이브 AI를 쓰되 클리어 수/보상/진행도에는 포함하지 않는 테스트 적.
 */
@UtilityClass
public class TestMobs {
    private static final int MAX_MOBS = 200;
    private static final Map<UUID, WaveMobController.MobState> MOBS = new HashMap<>();
    private static final Map<UUID, Summoner> SUMMONERS = new HashMap<>();

    public static boolean tracks(UUID id) {
        return MOBS.containsKey(id);
    }

    public static WaveMobController.@Nullable MobState state(UUID id) {
        return MOBS.get(id);
    }

    public static boolean spawn(ServerLevel level, EntityType<?> type, Vec3 pos, boolean elite, boolean breaker, CompoundTag nbt) {
        if (MOBS.size() >= MAX_MOBS) {
            return false;
        }

        if (!(type.create(level) instanceof Mob mob)) {
            return false;
        }

        mob.moveTo(pos.x, pos.y, pos.z, 0, 0);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.COMMAND, null);
        if (mob.getVehicle() != null) {
            mob.getVehicle().discard();
        }

        WaveMobs.prepare(mob, elite, breaker);
        if (!nbt.isEmpty()) {
            CompoundTag custom = nbt.copy();
            // Command identity/location and tracked root count cannot be overridden by entity data.
            for (String key : List.of("id", "UUID", "UUIDMost", "UUIDLeast", "Pos", "Passengers")) {
                custom.remove(key);
            }

            CompoundTag data = new CompoundTag();
            mob.saveWithoutId(data);
            data.merge(custom);
            mob.load(data);
            mob.moveTo(pos.x, pos.y, pos.z, mob.getYRot(), mob.getXRot());
            mob.addTag(WaveMobs.TAG);
            if (breaker) {
                mob.addTag(WaveMobs.BREAKER_TAG);
            }

            mob.setPersistenceRequired();
        }

        return mob.isAlive() && level.noCollision(mob) && track(level, mob);
    }

    /**
     * 웨이브 설정의 보스 능력치와 해당 웨이브 배율·규칙을 그대로 적용한다. kind 는 mini, boss, repeat_mini.
     *
     * @return 실패 사유, 성공하면 null
     */
    public static @Nullable String spawnBoss(ServerLevel level, String kind, int tier, Vec3 pos) {
        if (MOBS.size() >= MAX_MOBS) {
            return "테스트 적이 너무 많습니다.";
        }

        WaveSpec spec = bossSpec(kind, tier);
        if (spec == null) {
            return "웨이브 설정에 " + tier + "단계 " + kind + " 보스가 없습니다.";
        }

        WaveSpec.MobEntry entry = spec.mobs().getLast();
        ResourceLocation id = ResourceLocation.tryParse(entry.type());
        if (id == null || !(BuiltInRegistries.ENTITY_TYPE.get(id).create(level) instanceof Mob mob)) {
            return "소환할 수 없는 보스 종류: " + entry.type();
        }

        mob.moveTo(pos.x, pos.y, pos.z, 0, 0);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.COMMAND, null);
        if (mob.getVehicle() != null) {
            mob.getVehicle().discard();
        }

        WaveMobs.prepare(mob, false, true);
        WaveMobs.scale(mob, spec, entry);
        if (!level.noCollision(mob) || !track(level, mob)) {
            return "소환 위치에 빈 공간이 필요합니다.";
        }

        if (mob.getTags().contains(WaveSummoner.TAG)) {
            SUMMONERS.put(mob.getUUID(), new Summoner(new WaveSummoner(spec.rules()), spec));
        }

        return null;
    }

    private static @Nullable WaveSpec bossSpec(String kind, int tier) {
        String key = "repeat_mini".equals(kind) ? "repeatMini" : kind;
        JsonObject config = JsonParser.parseString(config()).getAsJsonObject();
        JsonArray tiers = config.getAsJsonArray("tiers");
        JsonArray bosses = config.getAsJsonArray("bosses");
        if (tier < 1 || tier > tiers.size() || tier > bosses.size() || !bosses.get(tier - 1).getAsJsonObject().has(key)) {
            return null;
        }

        // KubeJS 진행 규칙과 같다: 미니 보스는 5번째, 보스와 반복 미니 보스는 10번째 웨이브 구성에 붙는다.
        JsonObject stage = tiers.get(tier - 1).getAsJsonArray().get("mini".equals(kind) ? 4 : 9).getAsJsonObject().deepCopy();
        JsonObject boss = bosses.get(tier - 1).getAsJsonObject().getAsJsonObject(key).deepCopy();
        boss.addProperty("count", 1);
        boss.addProperty("breaker", true);
        boss.addProperty("boss", "boss".equals(kind) ? "boss" : "mini");
        JsonArray mobs = new JsonArray();
        mobs.add(boss);
        stage.add("mobs", mobs);
        stage.add("rules", config.getAsJsonObject("rules"));
        return WaveSpec.parse(stage.toString());
    }

    private static String config() {
        try {
            return WaveConfigFile.read(FMLPaths.GAMEDIR.get().resolve("kubejs/config/coredefender-waves.json"));
        } catch (IOException exception) {
            return WaveConfigFile.defaults();
        }
    }

    private static boolean track(ServerLevel level, Mob mob) {
        MOBS.put(mob.getUUID(), new WaveMobController.MobState());
        if (!level.addFreshEntity(mob)) {
            MOBS.remove(mob.getUUID());
            return false;
        }

        return true;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = WallManager.level(server);
        if (level == null || MOBS.isEmpty()) {
            return;
        }

        WallGeometry geo = WallManager.state(level).geometry();
        int margin = WaveSpawnArea.margin(geo);
        SUMMONERS.entrySet().removeIf(entry -> !MOBS.containsKey(entry.getKey()));
        for (Map.Entry<UUID, Summoner> entry : List.copyOf(SUMMONERS.entrySet())) {
            if (level.getEntity(entry.getKey()) instanceof Mob boss) {
                Summoner summoner = entry.getValue();
                for (Mob minion : summoner.summoner().tick(level, boss, geo, summoner.spec(), MAX_MOBS - MOBS.size())) {
                    track(level, minion);
                }
            }
        }

        for (UUID id : List.copyOf(MOBS.keySet())) {
            if (!(level.getEntity(id) instanceof Mob mob) || !mob.isAlive()) {
                MOBS.remove(id);
            } else {
                WaveMobController.MobState state = MOBS.get(id);
                if (state == null) {
                    continue;
                }

                if ((level.getGameTime() + mob.getId()) % 4 == 0) {
                    WaveMobController.tick(level, mob, state, geo, FlowFieldManager.get(level, margin));
                }

                WaveMobController.tickControls(mob, state);
            }
        }
    }

    public static void clear(MinecraftServer server) {
        ServerLevel level = WallManager.level(server);
        if (level != null) {
            for (UUID id : MOBS.keySet()) {
                Entity entity = level.getEntity(id);
                if (entity != null) {
                    entity.discard();
                }
            }
        }

        MOBS.clear();
        SUMMONERS.clear();
    }

    private record Summoner(WaveSummoner summoner, WaveSpec spec) {
    }
}
