package lkd.namsic.coredefender.kubejs;

import lkd.namsic.coredefender.wave.WaveManager;
import lkd.namsic.coredefender.wave.WaveSpec;
import lkd.namsic.coredefender.wave.WaveConfigFile;
import net.neoforged.fml.loading.FMLPaths;
import java.io.IOException;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import lombok.experimental.UtilityClass;
import net.minecraft.server.MinecraftServer;

/**
 * KubeJS 바인딩 CoreDefenderWave. 웨이브 구성은 JSON.stringify 한 문자열로 받는다 (WaveSpec 참고).
 */
@UtilityClass
// Methods are called by KubeJS through the registered binding.
@SuppressWarnings("unused")
public class CoreDefenderWave {
    /**
     * 시작에 성공하면 빈 문자열, 실패하면 사유
     */
    public static String start(MinecraftServer server, String specJson) {
        String error = WaveManager.start(server, WaveSpec.parse(specJson));
        return error == null ? "" : error;
    }

    public static boolean isMobType(MinecraftServer server, String id) {
        EntityType<?> type = EntityType.byString(id).orElse(null);
        return type != null && type.create(server.overworld()) instanceof Mob;
    }

    public static boolean isItemType(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null && BuiltInRegistries.ITEM.containsKey(location);
    }

    public static String defaultConfig() {
        return WaveConfigFile.defaults();
    }

    public static String readConfig() throws IOException {
        return WaveConfigFile.read(FMLPaths.GAMEDIR.get().resolve("kubejs/config/coredefender-waves.json"));
    }

    public static void abort(MinecraftServer server) {
        WaveManager.abort(server);
    }

    public static boolean isActive() {
        return WaveManager.isActive();
    }

    /**
     * { active, label?, remaining?, total? }
     */
    public static String info() {
        return WaveManager.info().toString();
    }
}
