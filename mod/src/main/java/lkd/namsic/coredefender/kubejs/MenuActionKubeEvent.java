package lkd.namsic.coredefender.kubejs;

import dev.latvian.mods.kubejs.player.KubePlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class MenuActionKubeEvent implements KubePlayerEvent {
    private final ServerPlayer player;
    private final String action;
    private final String arg;

    public MenuActionKubeEvent(ServerPlayer player, String action, String arg) {
        this.player = player;
        this.action = action;
        this.arg = arg;
    }

    @Override
    public Player getEntity() {
        return player;
    }

    public String getAction() {
        return action;
    }

    public String getArg() {
        return arg;
    }
}
