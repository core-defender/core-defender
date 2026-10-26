package lkd.namsic.coredefender.wall;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * 렌더링/충돌/폭발 판정이 공유하는 얇은 구간 면. 블록이나 엔티티를 생성하지 않는다.
 */
public final class BarrierGeometry {
    private BarrierGeometry() {
    }

    public static final double THICKNESS = 0.02;

    public record Panel(String id, AABB box, VoxelShape shape) {
        public Panel(String id, AABB box) {
            this(id, box, Shapes.create(box));
        }

        public Vec3 nearest(Vec3 point) {
            return new Vec3(Math.clamp(point.x, this.box.minX, this.box.maxX), Math.clamp(point.y, this.box.minY, this.box.maxY),
                Math.clamp(point.z, this.box.minZ, this.box.maxZ));
        }
    }

    public static List<Panel> panels(WallGeometry geo) {
        List<Panel> result = new ArrayList<>();
        double x0 = geo.outerMinX(), x1 = geo.outerMaxX() + 1;
        double z0 = geo.outerMinZ(), z1 = geo.outerMaxZ() + 1;
        for (int i = 0; i < geo.segmentsPerSide(); i++) {
            double a = i == 0 ? x0 : geo.minX() + 16 * i;
            double b = i == geo.segmentsPerSide() - 1 ? x1 : geo.minX() + 16 * (i + 1);
            result.add(new Panel("N" + i, new AABB(a, geo.baseY(), z0 - THICKNESS, b, geo.topY(), z0 + THICKNESS)));
            result.add(new Panel("S" + i, new AABB(a, geo.baseY(), z1 - THICKNESS, b, geo.topY(), z1 + THICKNESS)));
            a = i == 0 ? z0 : geo.minZ() + 16 * i;
            b = i == geo.segmentsPerSide() - 1 ? z1 : geo.minZ() + 16 * (i + 1);
            result.add(new Panel("W" + i, new AABB(x0 - THICKNESS, geo.baseY(), a, x0 + THICKNESS, geo.topY(), b)));
            result.add(new Panel("E" + i, new AABB(x1 - THICKNESS, geo.baseY(), a, x1 + THICKNESS, geo.topY(), b)));
        }

        return List.copyOf(result);
    }

    /**
     * 바닐라와 같은 축 순서로 이동량만 줄인다. 위치를 되돌리거나 경로를 다시 찾지 않는다.
     */
    public static Vec3 clipMovement(AABB box, Vec3 motion, List<VoxelShape> shapes) {
        double x = motion.x, y = motion.y, z = motion.z;
        y = Shapes.collide(Direction.Axis.Y, box, shapes, y);
        box = box.move(0, y, 0);
        boolean zFirst = Math.abs(x) < Math.abs(z);
        if (zFirst) {
            z = Shapes.collide(Direction.Axis.Z, box, shapes, z);
            box = box.move(0, 0, z);
        }

        x = Shapes.collide(Direction.Axis.X, box, shapes, x);
        if (!zFirst) {
            z = Shapes.collide(Direction.Axis.Z, box.move(x, 0, 0), shapes, z);
        }

        return new Vec3(x, y, z);
    }
}
