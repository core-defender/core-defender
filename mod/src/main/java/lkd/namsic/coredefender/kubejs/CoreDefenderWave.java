package lkd.namsic.coredefender.kubejs;

import lkd.namsic.coredefender.wave.WaveManager;
import lkd.namsic.coredefender.wave.WaveSpec;
import lombok.experimental.UtilityClass;
import net.minecraft.server.MinecraftServer;

/** KubeJS 바인딩 CoreDefenderWave. 웨이브 구성은 JSON.stringify 한 문자열로 받는다 (WaveSpec 참고). */
@UtilityClass
public class CoreDefenderWave {
    /** 시작에 성공하면 빈 문자열, 실패하면 사유 */
    public static String start(MinecraftServer server, String specJson) {
        String error = WaveManager.start(server, WaveSpec.parse(specJson));
        return error == null ? "" : error;
    }

    public static void abort(MinecraftServer server) {
        WaveManager.abort(server);
    }

    public static boolean isActive() {
        return WaveManager.isActive();
    }

    /** { active, label?, remaining?, total? } */
    public static String info() {
        return WaveManager.info().toString();
    }
}
