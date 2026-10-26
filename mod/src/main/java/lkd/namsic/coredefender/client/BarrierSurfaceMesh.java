package lkd.namsic.coredefender.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 평면을 겹치지 않는 파편과 테두리로 분할한다. 좌표/seed가 같으면 손상 패턴도 유지된다.
 */
public final class BarrierSurfaceMesh {
    private BarrierSurfaceMesh() {
    }

    public record Point(float u, float v) {
        Point toward(Point center, float fraction) {
            return new Point(this.u + (center.u - this.u) * fraction, this.v + (center.v - this.v) * fraction);
        }
    }

    public record Face(Point a, Point b, Point c, Point d, boolean edge) {
    }

    public static List<Face> create(float width, float height, float healthRatio, int seed) {
        if (healthRatio >= .75F) {
            return List.of(new Face(new Point(0, 0), new Point(width, 0),
                new Point(width, height), new Point(0, height), false));
        }

        float damage = Math.clamp((.75F - healthRatio) / .75F, 0, 1);
        int columns = Math.max(1, (int) Math.ceil(width / 2)), rows = Math.max(1, (int) Math.ceil(height / 1.3));
        float cellWidth = width / columns, cellHeight = height / rows;
        Random random = new Random(seed);
        Point[][] grid = new Point[rows + 1][columns + 1];
        for (int row = 0; row <= rows; row++) {
            for (int column = 0; column <= columns; column++) {
                float jitterX = (random.nextFloat() - .5F) * .3F * cellWidth;
                float jitterY = (random.nextFloat() - .5F) * .3F * cellHeight;
                grid[row][column] = new Point(column * cellWidth + (column == 0 || column == columns ? 0 : jitterX),
                    row * cellHeight + (row == 0 || row == rows ? 0 : jitterY));
            }
        }

        List<Face> faces = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                // 큰 손상에서만 일부 조각이 사라진다. 같은 난수를 사용해 손상 시 구멍이 점진적으로 늘어난다.
                if (random.nextFloat() < Math.max(0, damage - .5F) * .35F) {
                    continue;
                }

                Point[] corners = {grid[row][column], grid[row][column + 1], grid[row + 1][column + 1], grid[row + 1][column]};
                Point center = new Point((corners[0].u + corners[1].u + corners[2].u + corners[3].u) / 4,
                    (corners[0].v + corners[1].v + corners[2].v + corners[3].v) / 4);
                Point[] outer = new Point[4], inner = new Point[4];
                for (int i = 0; i < 4; i++) {
                    outer[i] = corners[i].toward(center, .006F + damage * .07F);
                    inner[i] = outer[i].toward(center, .012F + damage * .025F);
                }

                faces.add(new Face(inner[0], inner[1], inner[2], inner[3], false));
                for (int i = 0; i < 4; i++) {
                    int j = (i + 1) % 4;
                    faces.add(new Face(outer[i], outer[j], inner[j], inner[i], true));
                }
            }
        }

        return List.copyOf(faces);
    }
}
