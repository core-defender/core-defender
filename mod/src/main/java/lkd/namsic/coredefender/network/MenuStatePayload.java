package lkd.namsic.coredefender.network;

import io.netty.buffer.ByteBuf;
import lkd.namsic.coredefender.CoreDefender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 서버 → 클라이언트: 메뉴 상태(JSON). 상태 JSON 은 KubeJS 스크립트가 만든다.
 */
public record MenuStatePayload(int mode, String json) implements CustomPacketPayload {
    public static final int OPEN = 0;
    public static final int UPDATE = 1;
    public static final int CLOSE = 2;

    public static final Type<MenuStatePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "menu_state"));

    public static final StreamCodec<ByteBuf, MenuStatePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, MenuStatePayload::mode,
        ByteBufCodecs.stringUtf8(1 << 20), MenuStatePayload::json,
        MenuStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
