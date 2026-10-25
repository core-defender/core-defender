package lkd.namsic.coredefender.wave;

import lkd.namsic.coredefender.wall.WallGeometry;

/** 기지 전체를 감싸는 원형 띠. 고정 스폰 지점 없이 면적 기준으로 균등 추출한다. */
public final class WaveSpawnArea {
    private WaveSpawnArea() {}
    public record Point(double x, double z) {}

    public static double baseRadius(WallGeometry geo) {
        return Math.hypot((geo.outerMaxX() + 1.0 - geo.outerMinX()) / 2,
                (geo.outerMaxZ() + 1.0 - geo.outerMinZ()) / 2);
    }

    public static Point sample(WallGeometry geo, int minGap, int maxGap, double angleUnit, double areaUnit) {
        double inner = baseRadius(geo) + minGap;
        double outer = baseRadius(geo) + Math.max(minGap, maxGap);
        double radius = Math.sqrt(inner * inner + areaUnit * (outer * outer - inner * inner));
        double angle = angleUnit * Math.PI * 2;
        return new Point(geo.centerX() + Math.cos(angle) * radius, geo.centerZ() + Math.sin(angle) * radius);
    }

    /** 전체 원형 띠 + 이동 여유가 기존 사각형 기반 청크/플로우 필드 안에 들어가도록 한다. */
    public static int margin(WallGeometry geo, int maxGap) {
        double halfWidth = Math.min((geo.outerMaxX() + 1.0 - geo.outerMinX()) / 2,
                (geo.outerMaxZ() + 1.0 - geo.outerMinZ()) / 2);
        return (int) Math.ceil(baseRadius(geo) + maxGap - halfWidth) + 8;
    }
}
