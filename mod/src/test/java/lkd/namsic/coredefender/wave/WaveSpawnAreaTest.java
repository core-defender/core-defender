package lkd.namsic.coredefender.wave;

import lkd.namsic.coredefender.wall.WallGeometry;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class WaveSpawnAreaTest {
    @Test void samplesCoverAllDirectionsAndStayInsideLoadedAnnulusAfterExpansion() {
        for (int half : new int[]{1, 2, 4, 8}) {
            var geo = new WallGeometry(32, -48, 64, half, 5, 2);
            var random = new Random(42);
            int[] sectors = new int[16];
            var positions = new HashSet<String>();
            double inner = WaveSpawnArea.baseRadius(geo) + 16, outer = WaveSpawnArea.baseRadius(geo) + 64;
            int margin = WaveSpawnArea.margin(geo, 64);
            double totalArea = 0;
            for (int i = 0; i < 10000; i++) {
                double angle = random.nextDouble();
                var point = WaveSpawnArea.sample(geo, 16, 64, angle, random.nextDouble());
                sectors[(int) (angle * 16)]++;
                positions.add((int) Math.floor(point.x()) + ":" + (int) Math.floor(point.z()));
                double radius = Math.hypot(point.x() - geo.centerX(), point.z() - geo.centerZ());
                assertTrue(radius >= inner && radius <= outer);
                assertTrue(point.x() >= geo.outerMinX() - margin && point.x() <= geo.outerMaxX() + margin);
                assertTrue(point.z() >= geo.outerMinZ() - margin && point.z() <= geo.outerMaxZ() + margin);
                assertTrue(geo.distanceToWall(point.x(), point.z()) >= 16);
                totalArea += (radius * radius - inner * inner) / (outer * outer - inner * inner);
            }
            for (int count : sectors) assertTrue(count > 500 && count < 750);
            assertTrue(positions.size() > 4000, "Must not use a handful of spawn points");
            assertEquals(.5, totalArea / 10000, .015);
        }
    }
}
