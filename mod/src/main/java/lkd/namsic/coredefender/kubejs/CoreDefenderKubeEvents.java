package lkd.namsic.coredefender.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

public interface CoreDefenderKubeEvents {
    EventGroup GROUP = EventGroup.of("CoreDefenderEvents");

    /**
     * CoreDefenderEvents.menuAction(event => { event.player, event.action, event.arg })
     */
    EventHandler MENU_ACTION = GROUP.server("menuAction", () -> MenuActionKubeEvent.class);

    /**
     * CoreDefenderEvents.wallCollapsed(event => { event.server, event.segment })
     */
    EventHandler WALL_COLLAPSED = GROUP.server("wallCollapsed", () -> WallCollapsedKubeEvent.class);

    /**
     * CoreDefenderEvents.waveCleared(event => { event.server, event.label })
     */
    EventHandler WAVE_CLEARED = GROUP.server("waveCleared", () -> WaveEndKubeEvent.class);

    /**
     * CoreDefenderEvents.waveFailed(event => { event.server, event.label, event.reason })
     */
    EventHandler WAVE_FAILED = GROUP.server("waveFailed", () -> WaveEndKubeEvent.class);
}
