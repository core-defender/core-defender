package lkd.namsic.coredefender.kubejs;

import lkd.namsic.coredefender.network.MenuStatePayload;
import lombok.experimental.UtilityClass;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * KubeJS 바인딩 CoreDefenderUI. 상태는 스크립트에서 JSON.stringify 한 문자열로 받는다.
 */
@UtilityClass
// Methods are called by KubeJS through the registered binding.
@SuppressWarnings("unused")
public class CoreDefenderUI {

    /**
     * 메뉴 화면을 연다 (이미 열려 있으면 내용만 갱신).
     */
    public static void open(ServerPlayer player, String json) {
        PacketDistributor.sendToPlayer(player, new MenuStatePayload(MenuStatePayload.OPEN, json));
    }

    /**
     * 메뉴가 열려 있을 때만 내용을 갱신한다.
     */
    public static void update(ServerPlayer player, String json) {
        PacketDistributor.sendToPlayer(player, new MenuStatePayload(MenuStatePayload.UPDATE, json));
    }

    public static void close(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new MenuStatePayload(MenuStatePayload.CLOSE, ""));
    }
}
