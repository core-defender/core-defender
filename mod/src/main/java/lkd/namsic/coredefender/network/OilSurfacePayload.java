package lkd.namsic.coredefender.network;

import io.netty.buffer.ByteBuf;
import lkd.namsic.coredefender.CoreDefender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record OilSurfacePayload(ResourceLocation dimension, BlockPos turret, UUID identity, List<Cell> cells) implements CustomPacketPayload {
    public static final int MAX_CELLS = 96;
    public static final Type<OilSurfacePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "oil_surface"));
    public static final StreamCodec<ByteBuf, OilSurfacePayload> CODEC = StreamCodec.composite(
        ResourceLocation.STREAM_CODEC, OilSurfacePayload::dimension,
        BlockPos.STREAM_CODEC, OilSurfacePayload::turret,
        UUIDUtil.STREAM_CODEC, OilSurfacePayload::identity,
        Cell.CODEC.apply(ByteBufCodecs.list(MAX_CELLS)), OilSurfacePayload::cells,
        OilSurfacePayload::new);

    public record Cell(BlockPos block, float height, int remainingTicks) {
        public static final StreamCodec<ByteBuf, Cell> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Cell::block,
            ByteBufCodecs.FLOAT, Cell::height,
            ByteBufCodecs.VAR_INT, Cell::remainingTicks,
            Cell::new);
    }

    public OilSurfacePayload {
        cells = List.copyOf(cells);
        if (cells.size() > MAX_CELLS) {
            throw new IllegalArgumentException("Too many oil cells");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
