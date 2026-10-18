package lkd.namsic.coredefender.wave;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.wall.WallGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.function.LongPredicate;

/**
 * 성벽까지의 거리 지도. 성벽 바깥면에 붙은 칸이 0 이고, 몹은 값이 줄어드는 방향으로 이동한다.
 * <p>
 * 바리케이드 칸은 비용이 큰 통과 가능 칸으로 둔다 → 우회로가 충분히 짧으면 돌아가고,
 * 길이 막혀 있으면 경로가 바리케이드를 지나므로 몹이 그 앞에서 바리케이드를 공격하게 된다.
 * 디펜스 차원은 평지이므로 지면 높이 한 층만 본다.
 */
public final class FlowField {
    private static final int UNREACHABLE = Integer.MAX_VALUE;
    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private final WallGeometry geo;
    private final int minX;
    private final int minZ;
    private final int width;
    private final int depth;
    private final int[] dist;
    private final boolean[] barricade;

    /** 다음 이동 지점, 그리고 그 사이에 막고 있는 바리케이드(없으면 null) */
    public record Step(BlockPos waypoint, @Nullable BlockPos barricade) {
    }

    private FlowField(WallGeometry geo, int margin) {
        this.geo = geo;
        this.minX = geo.outerMinX() - margin;
        this.minZ = geo.outerMinZ() - margin;
        this.width = geo.outerMaxX() + margin - minX + 1;
        this.depth = geo.outerMaxZ() + margin - minZ + 1;
        this.dist = new int[width * depth];
        this.barricade = new boolean[width * depth];
        Arrays.fill(dist, UNREACHABLE);
    }

    public static FlowField compute(ServerLevel level, WallGeometry geo, int margin, LongPredicate isBarricade) {
        FlowField field = new FlowField(geo, margin);
        field.build(level, isBarricade);
        return field;
    }

    private int index(int x, int z) {
        int ix = x - minX;
        int iz = z - minZ;
        if (ix < 0 || iz < 0 || ix >= width || iz >= depth) {
            return -1;
        }
        return iz * width + ix;
    }

    private boolean insideWall(int x, int z) {
        return x >= geo.outerMinX() && x <= geo.outerMaxX() && z >= geo.outerMinZ() && z <= geo.outerMaxZ();
    }

    private void build(ServerLevel level, LongPredicate isBarricade) {
        int groundY = geo.baseY();
        boolean[] blocked = new boolean[width * depth];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));

        for (int iz = 0; iz < depth; iz++) {
            for (int ix = 0; ix < width; ix++) {
                int x = minX + ix;
                int z = minZ + iz;
                int i = iz * width + ix;
                if (insideWall(x, z)) {
                    blocked[i] = true;
                    continue;
                }
                pos.set(x, groundY, z);
                if (isBarricade.test(pos.asLong())) {
                    barricade[i] = true;
                } else if (solid(level, pos) || solid(level, pos.setY(groundY + 1))) {
                    blocked[i] = true;
                }
            }
        }

        // 시작점: 성벽 바깥면에 바로 붙은 칸
        for (int iz = 0; iz < depth; iz++) {
            for (int ix = 0; ix < width; ix++) {
                int i = iz * width + ix;
                if (blocked[i] || !touchesWall(minX + ix, minZ + iz)) {
                    continue;
                }
                dist[i] = barricade[i] ? Balance.BARRICADE_PATH_COST * 10 : 0;
                queue.add(new long[]{dist[i], i});
            }
        }

        while (!queue.isEmpty()) {
            long[] head = queue.poll();
            int i = (int) head[1];
            if (head[0] > dist[i]) {
                continue;
            }
            int x = minX + i % width;
            int z = minZ + i / width;
            for (int[] n : NEIGHBORS) {
                int j = index(x + n[0], z + n[1]);
                if (j < 0 || blocked[j]) {
                    continue;
                }
                // 대각선 이동은 두 직선 이웃이 모두 열려 있을 때만 (모서리 끼임 방지)
                if (n[0] != 0 && n[1] != 0) {
                    int a = index(x + n[0], z);
                    int b = index(x, z + n[1]);
                    if (a < 0 || b < 0 || blocked[a] || blocked[b]) {
                        continue;
                    }
                }
                int step = (n[0] != 0 && n[1] != 0 ? 14 : 10) + (barricade[j] ? Balance.BARRICADE_PATH_COST * 10 : 0);
                int next = dist[i] + step;
                if (next < dist[j]) {
                    dist[j] = next;
                    queue.add(new long[]{next, j});
                }
            }
        }
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private boolean touchesWall(int x, int z) {
        return !insideWall(x, z) && (insideWall(x + 1, z) || insideWall(x - 1, z) || insideWall(x, z + 1) || insideWall(x, z - 1));
    }

    /**
     * (x, z) 에서 거리 지도를 따라 최대 lookahead 칸 전진한 지점.
     * 도중에 바리케이드를 만나면 그 직전 칸과 바리케이드 위치를 돌려준다.
     * 지도 밖이거나 도달 불가 칸이면 null (호출 측에서 성벽으로 직진).
     */
    @Nullable
    public Step next(int x, int z, int lookahead) {
        int i = index(x, z);
        if (i < 0 || dist[i] == UNREACHABLE) {
            return null;
        }
        int cx = x;
        int cz = z;
        for (int s = 0; s < lookahead && dist[index(cx, cz)] > 0; s++) {
            int best = -1;
            int bestDist = dist[index(cx, cz)];
            for (int[] n : NEIGHBORS) {
                int j = index(cx + n[0], cz + n[1]);
                if (j >= 0 && dist[j] < bestDist) {
                    best = j;
                    bestDist = dist[j];
                }
            }
            if (best < 0) {
                break;
            }
            int nx = minX + best % width;
            int nz = minZ + best / width;
            if (barricade[best]) {
                return new Step(new BlockPos(cx, geo.baseY(), cz), new BlockPos(nx, geo.baseY(), nz));
            }
            cx = nx;
            cz = nz;
        }
        return new Step(new BlockPos(cx, geo.baseY(), cz), null);
    }
}
