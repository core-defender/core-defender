package lkd.namsic.coredefender.kubejs;

import dev.latvian.mods.kubejs.player.KubePlayerEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * CoreDefenderEvents.menuAction(event => { event.player, event.action, event.arg })
 */
@RequiredArgsConstructor
public class MenuActionKubeEvent implements KubePlayerEvent {
    private final ServerPlayer player;
    @Getter
    private final String action;
    @Getter
    private final String arg;

    @Override
    public Player getEntity() {
        return this.player;
    }
}
