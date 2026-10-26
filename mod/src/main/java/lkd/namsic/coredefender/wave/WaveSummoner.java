package lkd.namsic.coredefender.wave;

import lkd.namsic.coredefender.wall.WallGeometry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class WaveSummoner {
    public static final String TAG = "cd_summoner";
    private final Set<UUID> minions = new HashSet<>();
    private int cooldown;
    private int casts;

    public WaveSummoner(WaveRules rules) {
        this.cooldown = (int) Math.round(rules.summonInitialSeconds() * 20);
    }

    /** Returned mobs must be tracked by the wave before addFreshEntity fires join events. */
    public List<Mob> tick(ServerLevel level, Mob boss, WallGeometry geometry, WaveSpec spec, int capacity) {
        if (!boss.isAlive()) {
            return List.of();
        }

        this.minions.removeIf(id -> {
            Entity entity = level.getEntity(id);
            return entity == null || !entity.isAlive();
        });
        if (--this.cooldown > 0 || capacity <= 0 || this.minions.size() >= spec.rules().summonMaxAlive()) {
            return List.of();
        }

        this.cooldown = (int) Math.round(spec.rules().summonIntervalSeconds() * 20);
        this.casts++;
        boss.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 2, 0.8F);
        level.sendParticles(ParticleTypes.PORTAL, boss.getX(), boss.getY() + 1, boss.getZ(), 40, 1, 1, 1, .15);
        List<Mob> spawned = new ArrayList<>();
        int count = Math.min(spec.rules().summonBatchSize(), Math.min(capacity, spec.rules().summonMaxAlive() - this.minions.size()));
        for (int i = 0; i < count; i++) {
            EntityType<? extends Mob> type = i < count - 1 ? EntityType.VINDICATOR
                : this.casts % 2 == 0 ? EntityType.RAVAGER : EntityType.PILLAGER;
            Mob minion = type.create(level);
            if (minion == null) {
                continue;
            }

            boolean positioned = false;
            for (int attempt = 0; attempt < 16; attempt++) {
                double angle = level.random.nextDouble() * Math.PI * 2;
                double radius = 3 + level.random.nextDouble() * 5;
                int x = (int) Math.floor(boss.getX() + Math.cos(angle) * radius);
                int z = (int) Math.floor(boss.getZ() + Math.sin(angle) * radius);
                if (!level.getChunkSource().hasChunk(x >> 4, z >> 4) || geometry.distanceToWall(x + .5, z + .5) < 2
                    || geometry.isInterior(x, z)) {
                    continue;
                }

                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                minion.moveTo(x + .5, y, z + .5, boss.getYRot(), 0);
                if (level.noCollision(minion) && !level.containsAnyLiquid(minion.getBoundingBox())) {
                    positioned = true;
                    break;
                }
            }

            if (!positioned) {
                continue;
            }

            EventHooks.finalizeMobSpawn(minion, level, level.getCurrentDifficultyAt(minion.blockPosition()), MobSpawnType.EVENT, null);
            WaveSpec.MobEntry entry = new WaveSpec.MobEntry(EntityType.getKey(type).toString(), 1, false, true, "", 0, 0, 0);
            WaveMobs.prepare(minion, false, true);
            WaveMobs.scale(minion, spec, entry);
            this.minions.add(minion.getUUID());
            spawned.add(minion);
            level.sendParticles(ParticleTypes.POOF, minion.getX(), minion.getY() + 1, minion.getZ(), 16, .5, .7, .5, .03);
        }

        return spawned;
    }
}
