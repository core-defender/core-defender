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
        return centerX - 16 * half;
    }

    public int maxX() {
        return centerX + 16 * half - 1;
    }

    public int minZ() {
        return centerZ - 16 * half;
    }

    public int maxZ() {
        return centerZ + 16 * half - 1;
    }

    public int segmentsPerSide() {
        return 2 * half;
    }

    public boolean isInterior(int x, int z) {
        return x >= minX() && x <= maxX() && z >= minZ() && z <= maxZ();
    }

    public boolean isWallColumn(int x, int z) {
        return !isInterior(x, z)
                && x >= minX() - thickness && x <= maxX() + thickness
                && z >= minZ() - thickness && z <= maxZ() + thickness;
    }

    public boolean isWallBlock(BlockPos pos) {
        return pos.getY() >= baseY && pos.getY() < baseY + height && isWallColumn(pos.getX(), pos.getZ());
    }

    /** 성벽 기둥이 속한 구간 ID. 성벽이 아니면 null. */
    public String segmentAt(int x, int z) {
        if (!isWallColumn(x, z)) {
            return null;
        }
        if (z < minZ()) {
            return "N" + index(x - minX());
        }
        if (z > maxZ()) {
            return "S" + index(x - minX());
        }
        if (x < minX()) {
            return "W" + index(z - minZ());
        }
        return "E" + index(z - minZ());
    }

    private int index(int offset) {
        return Math.clamp(Math.floorDiv(offset, 16), 0, segmentsPerSide() - 1);
    }

    public List<String> segmentIds() {
        List<String> ids = new ArrayList<>();
        for (String side : SIDES) {
            for (int i = 0; i < segmentsPerSide(); i++) {
                ids.add(side + i);
            }
        }
        return ids;
    }

    /** 성벽 둘레의 모든 기둥 (x, z) */
    public List<int[]> columns() {
        List<int[]> columns = new ArrayList<>();
        for (int x = minX() - thickness; x <= maxX() + thickness; x++) {
            for (int z = minZ() - thickness; z <= maxZ() + thickness; z++) {
                if (isWallColumn(x, z)) {
                    columns.add(new int[]{x, z});
                }
            }
        }
        return columns;
    }

    public List<BlockPos> segmentBlocks(String segmentId) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int[] column : columns()) {
            if (segmentId.equals(segmentAt(column[0], column[1]))) {
                for (int y = baseY; y < baseY + height; y++) {
                    blocks.add(new BlockPos(column[0], y, column[1]));
                }
            }
        }
        return blocks;
    }

    /** 균열 표시용: 구간 중 바깥쪽 면의 블록만 */
    public List<BlockPos> segmentOuterFace(String segmentId) {
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : segmentBlocks(segmentId)) {
            int x = pos.getX();
            int z = pos.getZ();
            if (x == minX() - thickness || x == maxX() + thickness || z == minZ() - thickness || z == maxZ() + thickness) {
                blocks.add(pos);
            }
        }
        return blocks;
    }

    public BlockPos corePos() {
        return new BlockPos(centerX, baseY, centerZ);
    }

    /** 성벽 윗면(걸을 수 있는 높이)의 y */
    public int topY() {
        return baseY + height;
    }

    public int outerMinX() {
        return minX() - thickness;
    }

    public int outerMaxX() {
        return maxX() + thickness;
    }

    public int outerMinZ() {
        return minZ() - thickness;
    }

    public int outerMaxZ() {
        return maxZ() + thickness;
    }

    /** 성벽 바깥면까지의 수평 거리 (성벽 범위 안이면 0) */
    public double distanceToWall(double x, double z) {
        double dx = Math.max(Math.max(outerMinX() - x, 0), x - (outerMaxX() + 1));
        double dz = Math.max(Math.max(outerMinZ() - z, 0), z - (outerMaxZ() + 1));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 주어진 위치에서 가장 가까운 성벽 블록. 성 안쪽(돌파 후)이면 가장 가까운 안쪽 면. */
    public BlockPos nearestWallBlock(double x, double y, double z) {
        int bx = Math.clamp((int) Math.floor(x), outerMinX(), outerMaxX());
        int bz = Math.clamp((int) Math.floor(z), outerMinZ(), outerMaxZ());
        if (isInterior(bx, bz)) {
            int toWest = bx - minX();
            int toEast = maxX() - bx;
            int toNorth = bz - minZ();
            int toSouth = maxZ() - bz;
            int min = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));
            if (min == toWest) {
                bx = minX() - 1;
            } else if (min == toEast) {
                bx = maxX() + 1;
            } else if (min == toNorth) {
                bz = minZ() - 1;
            } else {
                bz = maxZ() + 1;
            }
        }
        int by = Math.clamp((int) Math.floor(y), baseY, baseY + height - 1);
        return new BlockPos(bx, by, bz);
    }
}
