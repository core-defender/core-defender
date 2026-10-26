package lkd.namsic.coredefender.wave;

import java.util.Comparator;
import java.util.function.BiPredicate;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.wall.WallGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

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

    /**
     * 다음 이동 지점, 그리고 그 사이에 막고 있는 바리케이드(없으면 null)
     */
    public record Step(BlockPos waypoint, @Nullable BlockPos barricade, boolean atWall) {
    }

    private FlowField(WallGeometry geo, int margin) {
        this.geo = geo;
        this.minX = geo.outerMinX() - margin;
        this.minZ = geo.outerMinZ() - margin;
        this.width = geo.outerMaxX() + margin - this.minX + 1;
        this.depth = geo.outerMaxZ() + margin - this.minZ + 1;
        this.dist = new int[this.width * this.depth];
        this.barricade = new boolean[this.width * this.depth];
        Arrays.fill(this.dist, UNREACHABLE);
    }

    public static FlowField compute(ServerLevel level, WallGeometry geo, int margin, LongPredicate isBarricade) {
        FlowField field = new FlowField(geo, margin);
        field.build((x, z) -> {
            BlockPos pos = new BlockPos(x, geo.baseY(), z);
            return solid(level, pos) || solid(level, pos.above());
        }, (x, z) -> isBarricade.test(new BlockPos(x, geo.baseY(), z).asLong()));
        return field;
    }

    private int index(int x, int z) {
        int ix = x - this.minX;
        int iz = z - this.minZ;
        if (ix < 0 || iz < 0 || ix >= this.width || iz >= this.depth) {
            return -1;
        }

        return iz * this.width + ix;
    }

    private boolean insideWall(int x, int z) {
        return x >= this.geo.outerMinX() && x <= this.geo.outerMaxX() && z >= this.geo.outerMinZ() && z <= this.geo.outerMaxZ();
    }

    static FlowField computeTestField(WallGeometry geo, BiPredicate<Integer, Integer> blocked,
                                      BiPredicate<Integer, Integer> barricade) {
        FlowField field = new FlowField(geo, 20);
        field.build(blocked, barricade);
        return field;
    }

    private void build(BiPredicate<Integer, Integer> solid, BiPredicate<Integer, Integer> isBarricade) {
        boolean[] blocked = new boolean[this.width * this.depth];
        PriorityQueue<long[]> queue = new PriorityQueue<>(Comparator.comparingLong(a -> a[0]));

        for (int iz = 0; iz < this.depth; iz++) {
            for (int ix = 0; ix < this.width; ix++) {
                int x = this.minX + ix;
                int z = this.minZ + iz;
                int i = iz * this.width + ix;
                if (this.insideWall(x, z)) {
                    blocked[i] = true;
                    continue;
                }

                if (isBarricade.test(x, z)) {
                    this.barricade[i] = true;
                } else if (solid.test(x, z)) {
                    blocked[i] = true;
                }
            }
        }

        // 시작점: 성벽 바깥면에 바로 붙은 칸
        for (int iz = 0; iz < this.depth; iz++) {
            for (int ix = 0; ix < this.width; ix++) {
                int i = iz * this.width + ix;
                if (blocked[i] || !this.touchesWall(this.minX + ix, this.minZ + iz)) {
                    continue;
                }

                this.dist[i] = this.barricade[i] ? Balance.BARRICADE_PATH_COST * 10 : 0;
                queue.add(new long[]{this.dist[i], i});
            }
        }

        while (!queue.isEmpty()) {
            long[] head = queue.poll();
            int i = (int) head[1];
            if (head[0] > this.dist[i]) {
                continue;
            }

            int x = this.minX + i % this.width;
            int z = this.minZ + i / this.width;
            for (int[] n : NEIGHBORS) {
                int j = this.index(x + n[0], z + n[1]);
                if (j < 0 || blocked[j]) {
                    continue;
                }

                // 대각선 이동은 두 직선 이웃이 모두 열려 있을 때만 (모서리 끼임 방지)
                if (n[0] != 0 && n[1] != 0) {
                    int a = this.index(x + n[0], z);
                    int b = this.index(x, z + n[1]);
                    if (a < 0 || b < 0 || blocked[a] || blocked[b]) {
                        continue;
                    }
                }

                int step = (n[0] != 0 && n[1] != 0 ? 14 : 10) + (this.barricade[j] ? Balance.BARRICADE_PATH_COST * 10 : 0);
                int next = this.dist[i] + step;
                if (next < this.dist[j]) {
                    this.dist[j] = next;
                    queue.add(new long[]{next, j});
                }
            }
        }
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private boolean touchesWall(int x, int z) {
        return !this.insideWall(x, z) && (this.insideWall(x + 1, z) || this.insideWall(x - 1, z) || this.insideWall(x, z + 1) || this.insideWall(x, z - 1));
    }

    public @Nullable Vec3 directApproach(Vec3 position, double bodyRadius) {
        double x = Math.clamp(position.x, this.geo.outerMinX(), this.geo.outerMaxX() + 1.0);
        double z = Math.clamp(position.z, this.geo.outerMinZ(), this.geo.outerMaxZ() + 1.0);
        Vec3 outward = new Vec3(position.x - x, 0, position.z - z);
        double standOff = bodyRadius + .35;
        if (outward.lengthSqr() <= standOff * standOff) {
            return null;
        }

        Vec3 destination = new Vec3(x, this.geo.baseY(), z).add(outward.normalize().scale(standOff));
        Vec3 delta = destination.subtract(position);
        int steps = Math.max(1, (int) Math.ceil(delta.horizontalDistance() * 2));
        for (int step = 0; step <= steps; step++) {
            double fraction = (double) step / steps;
            double px = position.x + delta.x * fraction;
            double pz = position.z + delta.z * fraction;
            for (int bx = (int) Math.floor(px - bodyRadius); bx <= (int) Math.floor(px + bodyRadius); bx++) {
                for (int bz = (int) Math.floor(pz - bodyRadius); bz <= (int) Math.floor(pz + bodyRadius); bz++) {
                    int index = this.index(bx, bz);
                    if (index < 0 || this.dist[index] == UNREACHABLE || this.barricade[index]) {
                        return null;
                    }
                }
            }
        }

        return destination;
    }

    /**
     * (x, z) 에서 거리 지도를 따라 최대 lookahead 칸 전진한 지점.
     * 도중에 바리케이드를 만나면 그 직전 칸과 바리케이드 위치를 돌려준다.
     * 지도 밖이거나 도달 불가 칸이면 null (호출 측에서 성벽으로 직진).
     */
    @Nullable
    public Step next(int x, int z, int lookahead) {
        int i = this.index(x, z);
        if (i < 0 || this.dist[i] == UNREACHABLE) {
            return null;
        }

        int cx = x;
        int cz = z;
        for (int s = 0; s < lookahead && this.dist[this.index(cx, cz)] > 0; s++) {
            int best = this.nextIndex(cx, cz);
            if (best < 0) {
                break;
            }

            int nx = this.minX + best % this.width;
            int nz = this.minZ + best / this.width;
            if (this.barricade[best]) {
                return new Step(new BlockPos(cx, this.geo.baseY(), cz), new BlockPos(nx, this.geo.baseY(), nz), false);
            }

            cx = nx;
            cz = nz;
        }

        return new Step(new BlockPos(cx, this.geo.baseY(), cz), null, this.touchesWall(cx, cz));
    }

    private int nextIndex(int cx, int cz) {
        int best = -1;
        int currentDist = this.dist[this.index(cx, cz)];
        int bestScore = Integer.MAX_VALUE;
        for (int[] n : NEIGHBORS) {
            int j = this.index(cx + n[0], cz + n[1]);
            if (n[0] != 0 && n[1] != 0) {
                int a = this.index(cx + n[0], cz), b = this.index(cx, cz + n[1]);
                if (a < 0 || b < 0 || this.dist[a] == UNREACHABLE || this.dist[b] == UNREACHABLE) {
                    continue;
                }
            }

            if (j >= 0 && this.dist[j] < currentDist) {
                int score = this.dist[j] + (n[0] != 0 && n[1] != 0 ? 14 : 10);
                if (score < bestScore) {
                    best = j;
                    bestScore = score;
                }
            }
        }

        return best;
    }
}
