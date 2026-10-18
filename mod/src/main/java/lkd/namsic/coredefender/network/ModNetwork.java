package lkd.namsic.coredefender.network;

import dev.latvian.mods.kubejs.script.ScriptType;
import lkd.namsic.coredefender.client.ClientHooks;
import lkd.namsic.coredefender.kubejs.CoreDefenderKubeEvents;
import lkd.namsic.coredefender.kubejs.MenuActionKubeEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(MenuStatePayload.TYPE, MenuStatePayload.CODEC, (payload, context) -> {
            // 전용 서버에서 클라이언트 클래스를 로드하지 않도록 분기 안에서만 참조
            if (FMLEnvironment.dist.isClient()) {
                ClientHooks.handleMenuState(payload);
            }
        });

        registrar.playToServer(MenuActionPayload.TYPE, MenuActionPayload.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                CoreDefenderKubeEvents.MENU_ACTION.post(ScriptType.SERVER, new MenuActionKubeEvent(player, payload.action(), payload.arg()));
            }
        });
    }
}
