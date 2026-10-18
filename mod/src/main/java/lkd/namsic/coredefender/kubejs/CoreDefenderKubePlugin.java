package lkd.namsic.coredefender.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

public class CoreDefenderKubePlugin implements KubeJSPlugin {
    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(CoreDefenderKubeEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        bindings.add("CoreDefenderUI", CoreDefenderUI.class);
        bindings.add("CoreDefenderWall", CoreDefenderWall.class);
    }
}
