package lkd.namsic.coredefender;

import com.mojang.logging.LogUtils;
import lkd.namsic.coredefender.network.ModNetwork;
import lkd.namsic.coredefender.registry.ModBlockEntities;
import lkd.namsic.coredefender.registry.ModBlocks;
import lkd.namsic.coredefender.registry.ModCreativeTab;
import lkd.namsic.coredefender.registry.ModItems;
import lkd.namsic.coredefender.supply.SupplyBlockEntity;
import lkd.namsic.coredefender.wave.WaveManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import org.slf4j.Logger;

@Mod(CoreDefender.MOD_ID)
public class CoreDefender {
    public static final String MOD_ID = "coredefender";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CoreDefender(IEventBus modBus) {
        ModItems.init();
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        modBus.addListener(ModNetwork::register);
        modBus.addListener(ModBlockEntities::registerCapabilities);
        modBus.addListener((RegisterTicketControllersEvent event) -> {
            event.register(WaveManager.TICKETS);
            event.register(SupplyBlockEntity.TICKETS);
        });
    }
}
