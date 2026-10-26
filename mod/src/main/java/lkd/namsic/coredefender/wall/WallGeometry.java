package lkd.namsic.coredefender.wall;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 사각형 성벽의 좌표 계산.
 * <p>
 * 내부는 (2 * half) x (2 * half) 청크이고 중심은 청크 경계에 맞춘다.
 * 성벽은 내부 바로 바깥을 thickness 두께로 두른다.
 * 구간 ID: 변(N/E/S/W) + 변 위의 청크 인덱스 (예: N0, N1). 모서리 기둥은 N/S 구간에 포함.
 */
public record WallGeometry(int centerX, int centerZ, int baseY, int half, int height, int thickness) {
    public static final String[] SIDES = {"N", "E", "S", "W"};

    public int minX() {
        return this.centerX - 16 * this.half;
    }

    public int maxX() {
        return this.centerX + 16 * this.half - 1;
    }

    public int minZ() {
        return this.centerZ - 16 * this.half;
    }

    public int maxZ() {
        return this.centerZ + 16 * this.half - 1;
    }

    public int segmentsPerSide() {
        return 2 * this.half;
    }

    public boolean isInterior(int x, int z) {
        return x >= this.minX() && x <= this.maxX() && z >= this.minZ() && z <= this.maxZ();
    }

    public boolean isWallColumn(int x, int z) {
        return !this.isInterior(x, z)
            && x >= this.minX() - this.thickness && x <= this.maxX() + this.thickness
            && z >= this.minZ() - this.thickness && z <= this.maxZ() + this.thickness;
    }

    public List<String> segmentIds() {
        List<String> ids = new ArrayList<>();
        for (String side : SIDES) {
            for (int i = 0; i < this.segmentsPerSide(); i++) {
                ids.add(side + i);
            }
        }

        return ids;
    }

    /**
     * 성벽 둘레의 모든 기둥 (x, z)
     */
    public List<int[]> columns() {
        List<int[]> columns = new ArrayList<>();
        for (int x = this.minX() - this.thickness; x <= this.maxX() + this.thickness; x++) {
            for (int z = this.minZ() - this.thickness; z <= this.maxZ() + this.thickness; z++) {
                if (this.isWallColumn(x, z)) {
                    columns.add(new int[]{x, z});
                }
            }
        }

        return columns;
    }

    public BlockPos corePos() {
        return new BlockPos(this.centerX, this.baseY, this.centerZ);
    }

    /**
     * 성벽 윗면(걸을 수 있는 높이)의 y
     */
    public int topY() {
        return this.baseY + this.height;
    }

    public int outerMinX() {
        return this.minX() - this.thickness;
    }

    public int outerMaxX() {
        return this.maxX() + this.thickness;
    }

    public int outerMinZ() {
        return this.minZ() - this.thickness;
    }

    public int outerMaxZ() {
        return this.maxZ() + this.thickness;
    }

    /**
     * 성벽 바깥면까지의 수평 거리 (성벽 범위 안이면 0)
     */
    public double distanceToWall(double x, double z) {
        double dx = Math.max(Math.max(this.outerMinX() - x, 0), x - (this.outerMaxX() + 1));
        double dz = Math.max(Math.max(this.outerMinZ() - z, 0), z - (this.outerMaxZ() + 1));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * 주어진 위치에서 가장 가까운 성벽 블록. 성 안쪽(돌파 후)이면 가장 가까운 안쪽 면.
     */
    public BlockPos nearestWallBlock(double x, double y, double z) {
        int bx = Math.clamp((int) Math.floor(x), this.outerMinX(), this.outerMaxX());
        int bz = Math.clamp((int) Math.floor(z), this.outerMinZ(), this.outerMaxZ());
        if (this.isInterior(bx, bz)) {
            int toWest = bx - this.minX();
            int toEast = this.maxX() - bx;
            int toNorth = bz - this.minZ();
            int toSouth = this.maxZ() - bz;
            int min = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));
            if (min == toWest) {
                bx = this.minX() - 1;
            } else if (min == toEast) {
                bx = this.maxX() + 1;
            } else if (min == toNorth) {
                bz = this.minZ() - 1;
            } else {
                bz = this.maxZ() + 1;
            }
        }

        int by = Math.clamp((int) Math.floor(y), this.baseY, this.baseY + this.height - 1);
        return new BlockPos(bx, by, bz);
    }
}
