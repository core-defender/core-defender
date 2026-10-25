package lkd.namsic.coredefender.kubejs;

import lkd.namsic.coredefender.supply.SupplyNetwork;
import lkd.namsic.coredefender.wall.CoreStorage;
import net.minecraft.world.item.ItemStack;

import lkd.namsic.coredefender.wall.WallManager;
import lombok.experimental.UtilityClass;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** KubeJS 바인딩 CoreDefenderWall. 상태 조회는 JSON 문자열(JSON.parse 해서 사용). */
@UtilityClass
public class CoreDefenderWall {
    /** 관리자 명령이 지정한 범위만 초기화한다. */
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public static boolean reset(MinecraftServer server, String scope) {
        ServerLevel level = WallManager.level(server);
        if (level == null) return false;
        WallManager.ensureBuilt(server);
        if (scope.equals("all") || scope.equals("facilities")) WallManager.clearFacilities(level);
        if (scope.equals("all") || scope.equals("storage")) {
            var storage = CoreStorage.get(level);
            storage.container().clearContent();
            storage.setDirty();
        }
        if (scope.equals("all") || scope.equals("supply")) {
            var buffer = SupplyNetwork.get(server).getBuffer();
            for (int i = 0; i < buffer.getSlots(); i++) buffer.setStackInSlot(i, ItemStack.EMPTY);
        }
        if (scope.equals("all") || scope.equals("default") || scope.equals("wall")) WallManager.reset(level, "wall");
        else if (scope.equals("size") || scope.equals("levels") || scope.equals("hp")) WallManager.reset(level, scope);
        return true;
    }
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
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
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public static void repair(MinecraftServer server, String segment, int amount) {
        ServerLevel level = WallManager.level(server);
        if (level != null) {
            WallManager.repair(level, segment == null || segment.isEmpty() ? null : segment, amount);
        }
    }

    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public static boolean anyCollapsed(MinecraftServer server) {
        ServerLevel level = WallManager.level(server);
        return level != null && WallManager.anyCollapsed(level);
    }

    /** side: N / E / S / W */
    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public static void upgradeSide(MinecraftServer server, String side) {
        ServerLevel level = WallManager.level(server);
        if (level != null) {
            WallManager.upgradeSide(level, side);
        }
    }

    // Registered entry point: invoked by the game registry, scripts, or annotation discovery.
    @SuppressWarnings("unused")
    public static void expand(MinecraftServer server) {
        ServerLevel level = WallManager.level(server);
        if (level != null) {
            WallManager.expand(level);
        }
    }
}
