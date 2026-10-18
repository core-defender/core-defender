package lkd.namsic.coredefender.client;

import lkd.namsic.coredefender.network.MenuStatePayload;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;

@UtilityClass
public class ClientHooks {
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
