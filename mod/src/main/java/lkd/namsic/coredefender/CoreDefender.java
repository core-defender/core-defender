package lkd.namsic.coredefender;

import lkd.namsic.coredefender.network.ModNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(CoreDefender.MOD_ID)
public class CoreDefender {
    public static final String MOD_ID = "coredefender";

    public CoreDefender(IEventBus modBus) {
        modBus.addListener(ModNetwork::register);
    }
}
