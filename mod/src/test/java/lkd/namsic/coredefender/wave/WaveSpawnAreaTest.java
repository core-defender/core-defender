package lkd.namsic.coredefender.wave;

import lkd.namsic.coredefender.wall.WallGeometry;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WaveSpawnAreaTest {
    @Test
    void everyWallBlockReceivesEqualSpawnProjectionInsteadOfCornerConcentration() {
        for (int half : new int[]{1, 2, 4}) {
            WallGeometry geo = new WallGeometry(32, -48, 64, half, 5, 0);
            int width = half * 32;
            int[][] arrivals = new int[4][width];
            int samples = width * 4 * 20;
            for (int i = 0; i < samples; i++) {
                WaveSpawnArea.Point point = WaveSpawnArea.sample(geo, (i + .5) / samples);
                boolean outsideX = point.x() < geo.outerMinX() || point.x() >= geo.outerMaxX() + 1;
                boolean outsideZ = point.z() < geo.outerMinZ() || point.z() >= geo.outerMaxZ() + 1;
                assertNotEquals(outsideX, outsideZ, "Diagonal corner regions must not attract extra spawns");
                if (outsideX) {
                    arrivals[point.x() < geo.centerX() ? 0 : 1][(int) Math.floor(point.z()) - geo.outerMinZ()]++;
                } else {
                    arrivals[point.z() < geo.centerZ() ? 2 : 3][(int) Math.floor(point.x()) - geo.outerMinX()]++;
                }
            }

            for (int[] face : arrivals) {
                for (int count : face) {
                    assertEquals(20, count);
                }
            }
        }
    }

    @Test
    void distanceScalesWithWallSizeAndStopsAt128() {
        int[] distances = {64, 85, 107, 128, 128};
        for (int half = 1; half <= distances.length; half++) {
            assertEquals(distances[half - 1], WaveSpawnArea.distance(new WallGeometry(0, 0, 64, half, 5, 0)));
        }
    }

    @Test
    void samplesStayAtWallDistanceAndInsideLoadedAreaAfterExpansion() {
        for (int half : new int[]{1, 2, 3, 4, 8}) {
            WallGeometry geo = new WallGeometry(32, -48, 64, half, 5, half % 2 == 0 ? 2 : 0);
            Random random = new Random(42);
            int[] sectors = new int[4];
            HashSet<String> positions = new HashSet<>();
            int gap = WaveSpawnArea.distance(geo);
            int margin = WaveSpawnArea.margin(geo);
            for (int i = 0; i < 10000; i++) {
                WaveSpawnArea.Point point = WaveSpawnArea.sample(geo, random.nextDouble());
                sectors[(point.x() >= geo.centerX() ? 1 : 0) + (point.z() >= geo.centerZ() ? 2 : 0)]++;
                positions.add((int) Math.floor(point.x()) + ":" + (int) Math.floor(point.z()));
                assertEquals(gap, geo.distanceToWall(point.x(), point.z()), 1e-8);
                assertEquals(gap, geo.distanceToWall(Math.floor(point.x()) + .5, Math.floor(point.z()) + .5), .71);
                assertTrue(point.x() >= geo.outerMinX() - margin && point.x() <= geo.outerMaxX() + margin);
                assertTrue(point.z() >= geo.outerMinZ() - margin && point.z() <= geo.outerMaxZ() + margin);
            }

            for (int count : sectors) {
                assertTrue(count > 2300 && count < 2700);
            }

            assertTrue(positions.size() >= (geo.outerMaxX() + 1 - geo.outerMinX()) * 4 * .95, "Must cover the full lengths of all four faces");
        }
    }

    @Test
    void waveSpecAcceptsScriptsWithOrWithoutLegacyRadius() {
        WaveSpec current = WaveSpec.parse("{\"mobs\":[],\"spawnInterval\":12,\"maxAlive\":60}");
        WaveSpec legacy = WaveSpec.parse("{\"mobs\":[],\"spawnInterval\":12,\"maxAlive\":60,\"spawnRadius\":{\"min\":12,\"max\":24}}");
        assertEquals(current, legacy, "Legacy stage radius must not override the wall-based distance");
    }
}
