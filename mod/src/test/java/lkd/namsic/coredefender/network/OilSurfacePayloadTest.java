package lkd.namsic.coredefender.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OilSurfacePayloadTest {
    @Test
    void snapshotPreservesPartialBlockHeightDimensionIdentityAndExpiry() {
        OilSurfacePayload payload = new OilSurfacePayload(ResourceLocation.parse("coredefender:defense"),
            new BlockPos(-20, 64, 30), UUID.randomUUID(), List.of(
            new OilSurfacePayload.Cell(new BlockPos(-21, 63, 30), .5F, 120),
            new OilSurfacePayload.Cell(new BlockPos(-22, 62, 30), 1, 8)));
        ByteBuf buffer = Unpooled.buffer();
        try {
            OilSurfacePayload.CODEC.encode(buffer, payload);
            assertEquals(payload, OilSurfacePayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void oversizedSnapshotsAreRejected() {
        OilSurfacePayload.Cell cell = new OilSurfacePayload.Cell(BlockPos.ZERO, 1, 120);
        assertThrows(IllegalArgumentException.class, () -> new OilSurfacePayload(ResourceLocation.parse("coredefender:defense"),
            BlockPos.ZERO, UUID.randomUUID(), Collections.nCopies(OilSurfacePayload.MAX_CELLS + 1, cell)));
    }
}
