package lkd.namsic.coredefender.kubejs;

import lkd.namsic.coredefender.wall.WallManager;
import lombok.experimental.UtilityClass;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** KubeJS 바인딩 CoreDefenderWall. 상태 조회는 JSON 문자열(JSON.parse 해서 사용). */
@UtilityClass
public class CoreDefenderWall {
    public static String info(MinecraftServer server) {
        ServerLevel level = WallManager.level(server);
        return level == null ? "{}" : WallManager.info(level).toString();
    }

    public static void damage(MinecraftServer server, String segment, int amount) {
        ServerLevel level = WallManager.level(server);
        if (level != null) {
            WallManager.damage(level, segment, amount);
        }
    }

    /** segment 가 null/빈 문자열이면 전체, amount 0 이하면 완전 수복 */
    public static void repair(MinecraftServer server, String segment, int amount) {
        ServerLevel level = WallManager.level(server);
        if (level != null) {
            WallManager.repair(level, segment == null || segment.isEmpty() ? null : segment, amount);
        }
    }

    public static boolean anyCollapsed(MinecraftServer server) {
        ServerLevel level = WallManager.level(server);
        return level != null && WallManager.anyCollapsed(level);
    }

    /** side: N / E / S / W */
    public static void upgradeSide(MinecraftServer server, String side) {
        ServerLevel level = WallManager.level(server);
        if (level != null) {
            WallManager.upgradeSide(level, side);
        }
    }

    public static void expand(MinecraftServer server) {
        ServerLevel level = WallManager.level(server);
        if (level != null) {
            WallManager.expand(level);
        }
    }
}
