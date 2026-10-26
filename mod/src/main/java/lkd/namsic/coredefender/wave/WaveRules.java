package lkd.namsic.coredefender.wave;

import com.google.gson.JsonObject;

/** Immutable per-wave settings; reloading the external file cannot change an active encounter. */
public record WaveRules(double spawnMinDistance, double spawnMaxDistance, double spawnAcceleration,
                        double bossRemainingFraction, double wardReduction, double wardSeconds,
                        double summonInitialSeconds, double summonIntervalSeconds, int summonMaxAlive,
                        int summonBatchSize, double summonerStandOff) {
    public static WaveRules parse(JsonObject json) {
        return new WaveRules(value(json, "spawnMinDistance", 64), value(json, "spawnMaxDistance", 128),
            value(json, "spawnAcceleration", .3), value(json, "bossRemainingFraction", .2),
            value(json, "wardReduction", .9), value(json, "wardSeconds", 60),
            value(json, "summonInitialSeconds", 10), value(json, "summonIntervalSeconds", 15),
            (int) value(json, "summonMaxAlive", 12), (int) value(json, "summonBatchSize", 3),
            value(json, "summonerStandOff", 18));
    }

    private static double value(JsonObject json, String name, double fallback) {
        return json.has(name) ? json.get(name).getAsDouble() : fallback;
    }
}
