package lkd.namsic.coredefender.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.server.MinecraftServer;

/**
 * CoreDefenderEvents.waveCleared / waveFailed (event => { event.server, event.label, event.success, event.reason })
 * reason: cleared | wall_collapsed
 */
@Getter
@RequiredArgsConstructor
public class WaveEndKubeEvent implements KubeEvent {
    private final MinecraftServer server;
    private final String label;
    private final boolean success;
    private final String reason;
}
