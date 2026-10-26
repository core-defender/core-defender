package lkd.namsic.coredefender.wave;

import lkd.namsic.coredefender.wall.WallGeometry;

/**
 * Samples equal lengths along the four wall faces, then offsets outward along each face normal.
 */
public final class WaveSpawnArea {
    private WaveSpawnArea() {
    }

    public record Point(double x, double z) {
    }

    public static int distance(WallGeometry geo) {
        return distance(geo, 64, 128);
    }

    public static int distance(WallGeometry geo, double minimum, double maximum) {
        return (int) Math.round(minimum + (maximum - minimum) * Math.clamp((geo.half() - 1) / 3.0, 0, 1));
    }

    public static Point sample(WallGeometry geo, double perimeterUnit) {
        return sample(geo, perimeterUnit, distance(geo));
    }

    public static Point sample(WallGeometry geo, double perimeterUnit, double gap) {
        double width = geo.outerMaxX() + 1.0 - geo.outerMinX();
        double halfWidth = width / 2;
        double quarter = perimeterUnit * 4;
        int side = Math.min(3, (int) quarter);
        // Corner arcs all converge on the same corner, so only sample the face's projection strip.
        double x = -halfWidth + (quarter - side) * width;
        double z = -halfWidth - gap;

        for (int i = 0; i < side; i++) {
            double previousX = x;
            x = -z;
            z = previousX;
        }

        return new Point(geo.centerX() + x, geo.centerZ() + z);
    }

    /**
     * 스폰 둘레와 이동 여유를 청크 로딩 및 경로 탐색 범위에 포함한다.
     */
    public static int margin(WallGeometry geo) {
        return distance(geo) + 8;
    }
}
