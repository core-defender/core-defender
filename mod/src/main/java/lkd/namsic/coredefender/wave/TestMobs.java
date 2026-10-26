package lkd.namsic.coredefender.wave;

import org.jspecify.annotations.Nullable;

import lkd.namsic.coredefender.wall.WallGeometry;
import net.minecraft.world.entity.Entity;

import lkd.namsic.coredefender.wall.WallManager;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 실제 웨이브 AI를 쓰되 클리어 수/보상/진행도에는 포함하지 않는 테스트 적.
 */
@UtilityClass
public class TestMobs {
    private static final Map<UUID, WaveMobController.MobState> MOBS = new HashMap<>();

    public static boolean tracks(UUID id) {
        return MOBS.containsKey(id);
    }

    public static WaveMobController.@Nullable MobState state(UUID id) {
        return MOBS.get(id);
    }

    public static boolean spawn(ServerLevel level, EntityType<?> type, Vec3 pos, boolean elite, boolean breaker, CompoundTag nbt) {
        if (MOBS.size() >= 200) {
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

        if (!mob.isAlive() || !level.noCollision(mob)) {
            return false;
        }

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
    }
}
