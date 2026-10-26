package lkd.namsic.coredefender.wall;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import lkd.namsic.coredefender.network.BarrierStatePayload;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class BarrierSync {
    private BarrierSync() {
    }

    private static String previous = "";

    /**
     * 5틱마다 변경을 묶어 보내고, 입장/재접속 누락 방지를 위해 1초마다 재전송한다.
     */
    public static void tick(MinecraftServer server) {
        ServerLevel level = WallManager.level(server);
        if (level == null || level.players().isEmpty()) {
            return;
        }

        String json = WallManager.info(level).toString();
        if (!json.equals(previous) || server.getTickCount() % 20 == 0) {
            previous = json;
            BarrierStatePayload payload = new BarrierStatePayload(json);
            for (ServerPlayer player : level.players()) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
