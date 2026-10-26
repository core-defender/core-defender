package lkd.namsic.coredefender.network;

import io.netty.buffer.ByteBuf;
import lkd.namsic.coredefender.CoreDefender;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BarrierStatePayload(String json) implements CustomPacketPayload {
    public static final Type<BarrierStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "barrier_state"));
    public static final StreamCodec<ByteBuf, BarrierStatePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(65536), BarrierStatePayload::json, BarrierStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
