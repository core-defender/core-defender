package lkd.namsic.coredefender.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BarrierSurfaceMeshTest {
    @Test
    void healthySurfaceIsWholeAndDamageOpensMoreGaps() {
        float width = 15.94F, height = 4.9F;
        double previous = width * height + .001;
        for (float hp : new float[]{1, .7F, .5F, .25F, .05F}) {
            List<BarrierSurfaceMesh.Face> mesh = BarrierSurfaceMesh.create(width, height, hp, 42);
            double area = 0;
            for (BarrierSurfaceMesh.Face face : mesh) {
                List<BarrierSurfaceMesh.Point> points = List.of(face.a(), face.b(), face.c(), face.d());
                double twiceArea = 0;
                for (int i = 0; i < 4; i++) {
                    BarrierSurfaceMesh.Point a = points.get(i);
                    BarrierSurfaceMesh.Point b = points.get((i + 1) % 4);
                    assertTrue(a.u() >= 0 && a.u() <= width && a.v() >= 0 && a.v() <= height);
                    twiceArea += a.u() * b.v() - b.u() * a.v();
                }

                assertTrue(twiceArea > 0, "No inverted or degenerate faces");
                area += twiceArea / 2;
            }

            assertTrue(area <= previous + .0001, "Damage should increase gaps, not add overlapping surface");
            previous = area;
            assertEquals(mesh, BarrierSurfaceMesh.create(width, height, hp, 42), "Pattern must not flicker between updates");
        }

        assertEquals(1, BarrierSurfaceMesh.create(width, height, 1, 42).size());
    }
}
