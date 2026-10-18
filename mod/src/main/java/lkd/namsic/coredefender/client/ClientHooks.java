package lkd.namsic.coredefender.client;

import lkd.namsic.coredefender.network.MenuStatePayload;
import net.minecraft.client.Minecraft;

public final class ClientHooks {
    private ClientHooks() {
    }

    public static void handleMenuState(MenuStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        switch (payload.mode()) {
            case MenuStatePayload.OPEN -> {
                if (mc.screen instanceof CoreDefenderScreen screen) {
                    screen.update(payload.json());
                } else {
                    mc.setScreen(new CoreDefenderScreen(payload.json()));
                }
            }
            case MenuStatePayload.UPDATE -> {
                if (mc.screen instanceof CoreDefenderScreen screen) {
                    screen.update(payload.json());
                }
            }
            case MenuStatePayload.CLOSE -> {
                if (mc.screen instanceof CoreDefenderScreen) {
                    mc.setScreen(null);
                }
            }
            default -> {
            }
        }
    }
}
