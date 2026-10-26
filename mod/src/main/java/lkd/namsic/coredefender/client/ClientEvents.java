package lkd.namsic.coredefender.client;

import com.mojang.blaze3d.platform.InputConstants;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.network.MenuActionPayload;
import lombok.experimental.UtilityClass;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@UtilityClass
public class ClientEvents {
    public static final KeyMapping MENU_KEY = new KeyMapping(
        "key.coredefender.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.coredefender");

    @EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(MENU_KEY);
        }
    }

    @EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (MENU_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    // 서버(KubeJS)가 상태를 만들어 MenuStatePayload(OPEN) 으로 응답한다
                    PacketDistributor.sendToServer(new MenuActionPayload("open", ""));
                }
            }
        }
    }
}
