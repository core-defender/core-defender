package lkd.namsic.coredefender.event;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.wall.WallManager;
import lombok.experimental.UtilityClass;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import lkd.namsic.coredefender.wave.WaveManager;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = CoreDefender.MOD_ID)
@UtilityClass
public class ServerLifecycleEvents {
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        WaveManager.abort(event.getServer());
    }

    private static final int BARRIER_SYNC_INTERVAL = 5;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        WallManager.ensureBuilt(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % BARRIER_SYNC_INTERVAL == 0) {
            WallManager.syncState(event.getServer());
        }
    }
}
