package lkd.namsic.coredefender.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.MinecraftServer;

/** CoreDefenderEvents.wallCollapsed(event => { event.server, event.segment }) */
public class WallCollapsedKubeEvent implements KubeEvent {
    private final MinecraftServer server;
    private final String segment;

    public WallCollapsedKubeEvent(MinecraftServer server, String segment) {
        this.server = server;
        this.segment = segment;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public String getSegment() {
        return segment;
    }
}
