package lkd.namsic.coredefender.registry;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.turret.TurretModule;
import lombok.experimental.UtilityClass;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@UtilityClass
public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CoreDefender.MOD_ID);

    // The deferred registry invokes this entry without a Java reference to its holder.
    @SuppressWarnings("unused")
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.coredefender"))
        .icon(() -> ModBlocks.CORE_ITEM.get().getDefaultInstance())
        .displayItems((parameters, output) -> ModBlocks.ITEMS.getEntries().stream()
            .filter(item -> !(item.get() instanceof TurretModule module) || !module.legacy)
            .forEach(item -> output.accept(item.get())))
        .build());
}
