package lkd.namsic.coredefender.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.server.MinecraftServer;

/**
 * CoreDefenderEvents.wallCollapsed(event => { event.server, event.segment })
 */
// KubeJS exposes bean getters as event properties.
@SuppressWarnings("ClassCanBeRecord")
@Getter
@RequiredArgsConstructor
public class WallCollapsedKubeEvent implements KubeEvent {
    private final MinecraftServer server;
    private final String segment;
}
