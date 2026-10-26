package lkd.namsic.coredefender.client;

import lkd.namsic.coredefender.wall.BarrierGeometry;
import net.minecraft.world.phys.AABB;

/** Visual envelope separated from integer block faces and from the collision geometry. */
final class BarrierRenderGeometry {
    private static final double OFFSET = .01;

    private BarrierRenderGeometry() {
    }

    static AABB bounds(BarrierGeometry.Panel panel, int segmentsPerSide) {
        AABB box = panel.box();
        char side = panel.id().charAt(0);
        int index = Integer.parseInt(panel.id().substring(1));
        double start = index == 0 ? OFFSET : 0;
        double end = index == segmentsPerSide - 1 ? OFFSET : 0;
        if (side == 'N' || side == 'S') {
            double z = (box.minZ + box.maxZ) / 2 + (side == 'N' ? -OFFSET : OFFSET);
            return new AABB(box.minX - start, box.minY, z, box.maxX + end, box.maxY, z);
        }

        double x = (box.minX + box.maxX) / 2 + (side == 'W' ? -OFFSET : OFFSET);
        return new AABB(x, box.minY, box.minZ - start, x, box.maxY, box.maxZ + end);
    }
}
