package lkd.namsic.coredefender.client;

import lkd.namsic.coredefender.wall.BarrierGeometry;
import lkd.namsic.coredefender.wall.WallGeometry;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrierRenderGeometryTest {
    @Test
    void facesAvoidBlockPlanesAndMeetAtCornersAndSegmentSeams() {
        WallGeometry wall = new WallGeometry(0, 0, 64, 1, 5, 0);
        Map<String, AABB> boxes = new HashMap<>();
        for (BarrierGeometry.Panel panel : BarrierGeometry.panels(wall)) {
            boxes.put(panel.id(), BarrierRenderGeometry.bounds(panel, wall.segmentsPerSide()));
        }

        assertEquals(-16.01, Objects.requireNonNull(boxes.get("N0")).minZ, 1e-6);
        assertEquals(16.01, Objects.requireNonNull(boxes.get("S0")).minZ, 1e-6);
        assertEquals(-16.01, Objects.requireNonNull(boxes.get("W0")).minX, 1e-6);
        assertEquals(16.01, Objects.requireNonNull(boxes.get("E0")).minX, 1e-6);
        assertEquals(Objects.requireNonNull(boxes.get("N0")).minX, Objects.requireNonNull(boxes.get("W0")).minX);
        assertEquals(Objects.requireNonNull(boxes.get("N0")).minZ, Objects.requireNonNull(boxes.get("W0")).minZ);
        assertEquals(Objects.requireNonNull(boxes.get("S1")).maxX, Objects.requireNonNull(boxes.get("E1")).maxX);
        assertEquals(Objects.requireNonNull(boxes.get("S1")).maxZ, Objects.requireNonNull(boxes.get("E1")).maxZ);
        assertEquals(Objects.requireNonNull(boxes.get("N0")).maxX, Objects.requireNonNull(boxes.get("N1")).minX);
        assertEquals(Objects.requireNonNull(boxes.get("E0")).maxZ, Objects.requireNonNull(boxes.get("E1")).minZ);
    }

    @Test
    void localVerticesPreserveOffsetNearWorldBorder() {
        WallGeometry wall = new WallGeometry(29_000_000, 29_000_000, 64, 4, 5, 0);
        BarrierGeometry.Panel panel = BarrierGeometry.panels(wall).getFirst();
        Vec3 origin = panel.box().getCenter();
        AABB rendered = BarrierRenderGeometry.bounds(panel, wall.segmentsPerSide()).move(-origin.x, -origin.y, -origin.z);
        assertEquals(-.01F, (float) rendered.minZ, .000001F);
        assertTrue(rendered.minX < panel.box().minX - origin.x);
        assertEquals(wall.outerMinZ(), origin.z, 1e-6, "Physical wall remains on the original boundary");
    }
}
