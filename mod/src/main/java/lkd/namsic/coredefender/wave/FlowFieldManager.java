package lkd.namsic.coredefender.wave;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import lkd.namsic.coredefender.killzone.KillzoneManager;
import lkd.namsic.coredefender.wall.WallGeometry;
import lkd.namsic.coredefender.wall.WallManager;
import lombok.experimental.UtilityClass;
import net.minecraft.server.level.ServerLevel;

/**
 * 플로우 필드 캐시. 바리케이드 설치/파괴나 성벽 확장 시 무효화되고 다음 요청 때 다시 계산한다.
 */
@UtilityClass
public class FlowFieldManager {
    private static @Nullable FlowField cached;
    private static @Nullable WallGeometry cachedGeometry;
    private static int cachedMargin;

    public static void invalidate() {
        cached = null;
    }

    public static FlowField get(ServerLevel level, int margin) {
        WallGeometry geo = WallManager.state(level).geometry();
        if (cached == null || !geo.equals(cachedGeometry) || margin != cachedMargin) {
            Long2IntOpenHashMap barricades = KillzoneManager.state(level).hp();
            cached = FlowField.compute(level, geo, margin, barricades::containsKey);
            cachedGeometry = geo;
            cachedMargin = margin;
        }

        return cached;
    }
}
