package lkd.namsic.coredefender.wave;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.List;

/**
 * KubeJS 가 넘겨주는 웨이브 구성.
 * <pre>
 * { label, mobs: [{ type, count, elite?, breaker? }], maxAlive?, spawnInterval? }
 * </pre>
 */
public record WaveSpec(String label, List<MobEntry> mobs, int maxAlive, int spawnInterval, int spawnBatchSize, double healthMultiplier, double damageMultiplier, double speedMultiplier, WaveRules rules) {
    public record MobEntry(String type, int count, boolean elite, boolean breaker, String boss, double health, double attack, double armor) {
    }

    public static WaveSpec parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<MobEntry> mobs = new ArrayList<>();
        for (JsonElement el : root.getAsJsonArray("mobs")) {
            JsonObject mob = el.getAsJsonObject();
            mobs.add(new MobEntry(
                mob.get("type").getAsString(),
                mob.get("count").getAsInt(),
                mob.has("elite") && mob.get("elite").getAsBoolean(),
                mob.has("breaker") && mob.get("breaker").getAsBoolean(),
                mob.has("boss") ? mob.get("boss").getAsString() : "",
                number(mob, "health", 0, 0, 1024),
                number(mob, "attack", 0, 0, 2048),
                number(mob, "armor", 0, 0, 30)));
        }

        return new WaveSpec(
            root.has("label") ? root.get("label").getAsString() : "",
            mobs,
            root.has("maxAlive") ? root.get("maxAlive").getAsInt() : 60,
            root.has("spawnInterval") ? Math.max(1, root.get("spawnInterval").getAsInt()) : 10,
            (int) number(root, "spawnBatchSize", 1, 1, 16),
            number(root, "healthMultiplier", 1, .1, 32),
            number(root, "damageMultiplier", 1, 1, 32),
            number(root, "speedMultiplier", 1, 1, 2),
            WaveRules.parse(root.has("rules") ? root.getAsJsonObject("rules") : new JsonObject()));
    }

    private static double number(JsonObject json, String key, double fallback, double min, double max) {
        double value = json.has(key) ? json.get(key).getAsDouble() : fallback;
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }

    public List<MobEntry> spawnQueue(Random random) {
        List<MobEntry> queue = new ArrayList<>();
        for (MobEntry entry : this.mobs) {
            for (int i = 0; i < entry.count(); i++) {
                queue.add(entry);
            }
        }

        Collections.shuffle(queue, random);
        // Stable partition keeps boss entries behind every normal spawn.
        queue.sort((left, right) -> Boolean.compare(!left.boss().isEmpty(), !right.boss().isEmpty()));
        return queue;
    }

    public boolean canSpawn(MobEntry entry, int alive) {
        int normalTotal = this.mobs.stream().filter(mob -> mob.boss().isEmpty()).mapToInt(MobEntry::count).sum();
        return entry.boss().isEmpty() || alive <= Math.max(1, (int) Math.floor(normalTotal * this.rules.bossRemainingFraction()));
    }

    public int total() {
        return this.mobs.stream().mapToInt(MobEntry::count).sum();
    }
}
