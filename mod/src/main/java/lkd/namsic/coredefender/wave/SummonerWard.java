package lkd.namsic.coredefender.wave;

import net.minecraft.world.entity.LivingEntity;

public final class SummonerWard {
    private static final String START = "cd_summoner_ward_start";

    private SummonerWard() {
    }

    public static void start(LivingEntity entity, WaveRules rules) {
        // The engine owns this level; borrowers must not close it.
        //noinspection resource
        entity.getPersistentData().putLong(START, entity.level().getGameTime());
        entity.getPersistentData().putDouble("cd_ward_strength", rules.wardReduction());
        entity.getPersistentData().putDouble("cd_ward_ticks", rules.wardSeconds() * 20);
    }

    public static double reduction(LivingEntity entity) {
        if (!entity.getTags().contains(WaveSummoner.TAG) || !entity.getPersistentData().contains(START)) {
            return 0;
        }

        // The engine owns this level; borrowers must not close it.
        //noinspection resource
        return reductionAt(entity.level().getGameTime() - entity.getPersistentData().getLong(START),
            entity.getPersistentData().getDouble("cd_ward_strength"), entity.getPersistentData().getDouble("cd_ward_ticks"));
    }

    public static double reductionAt(long elapsedTicks) {
        return reductionAt(elapsedTicks, .9, 1200);
    }

    public static double reductionAt(long elapsedTicks, double strength, double duration) {
        return duration <= 0 ? 0 : strength * (1 - Math.clamp(elapsedTicks / duration, 0, 1));
    }
}
