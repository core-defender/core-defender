package lkd.namsic.coredefender.network;

import io.netty.buffer.ByteBuf;
import lkd.namsic.coredefender.CoreDefender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 클라이언트 → 서버: 메뉴 동작 (open, refresh, start, repair, enter, leave ...).
 * 서버에서는 KubeJS 이벤트 CoreDefenderEvents.menuAction 으로 전달된다.
 */
public record MenuActionPayload(String action, String arg) implements CustomPacketPayload {
    public static final Type<MenuActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "menu_action"));

    public static final StreamCodec<ByteBuf, MenuActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), MenuActionPayload::action,
            ByteBufCodecs.stringUtf8(256), MenuActionPayload::arg,
            MenuActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
