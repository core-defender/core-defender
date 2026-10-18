package lkd.namsic.coredefender.wave;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * KubeJS 가 넘겨주는 웨이브 구성.
 * <pre>
 * { label, mobs: [{ type, count, elite?, breaker? }], spawnRadius: { min, max }, maxAlive?, spawnInterval? }
 * </pre>
 */
public record WaveSpec(String label, List<MobEntry> mobs, int spawnMin, int spawnMax, int maxAlive, int spawnInterval) {
    public record MobEntry(String type, int count, boolean elite, boolean breaker) {
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
                    mob.has("breaker") && mob.get("breaker").getAsBoolean()));
        }
        JsonObject radius = root.getAsJsonObject("spawnRadius");
        return new WaveSpec(
                root.has("label") ? root.get("label").getAsString() : "",
                mobs,
                radius.get("min").getAsInt(),
                radius.get("max").getAsInt(),
                root.has("maxAlive") ? root.get("maxAlive").getAsInt() : 60,
                root.has("spawnInterval") ? root.get("spawnInterval").getAsInt() : 10);
    }

    public int total() {
        return mobs.stream().mapToInt(MobEntry::count).sum();
    }
}
