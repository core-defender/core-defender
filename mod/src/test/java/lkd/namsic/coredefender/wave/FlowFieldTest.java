package lkd.namsic.coredefender.wave;

import lkd.namsic.coredefender.wall.WallGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlowFieldTest {
    private final WallGeometry geo = new WallGeometry(0, 0, 64, 1, 5, 2);

    @Test
    void directApproachPreservesFractionalPositionAlongEveryWallFace() {
        FlowField field = FlowField.computeTestField(this.geo, (x, z) -> false, (x, z) -> false);
        assertEquals(new Vec3(2.13, 64, -18.65), field.directApproach(new Vec3(2.13, 64, -30.2), .3));
        assertEquals(new Vec3(2.13, 64, 18.65), field.directApproach(new Vec3(2.13, 64, 30.2), .3));
        assertEquals(new Vec3(-18.65, 64, 2.13), field.directApproach(new Vec3(-30.2, 64, 2.13), .3));
        assertEquals(new Vec3(18.65, 64, 2.13), field.directApproach(new Vec3(30.2, 64, 2.13), .3));
    }

    @Test
    void directApproachFallsBackForObstaclesAndBarricadesIncludingBodyWidth() {
        FlowField solid = FlowField.computeTestField(this.geo, (x, z) -> x == 0 && z == -24, (x, z) -> false);
        FlowField barricade = FlowField.computeTestField(this.geo, (x, z) -> false, (x, z) -> x == 0 && z == -24);
        for (FlowField field : new FlowField[]{solid, barricade}) {
            assertNull(field.directApproach(new Vec3(.5, 64, -30), .3));
            assertNull(field.directApproach(new Vec3(1.1, 64, -30), .3));
            assertNotNull(field.directApproach(new Vec3(2.13, 64, -30), .3));
        }
    }

    @Test
    void openSidesApproachStraightWithoutDriftingToCorners() {
        FlowField field = FlowField.computeTestField(this.geo, (x, z) -> false, (x, z) -> false);
        for (int offset = -12; offset <= 12; offset++) {
            this.assertApproach(field, new BlockPos(offset, 64, -30), new BlockPos(offset, 64, -19));
            this.assertApproach(field, new BlockPos(offset, 64, 30), new BlockPos(offset, 64, 18));
            this.assertApproach(field, new BlockPos(-30, 64, offset), new BlockPos(-19, 64, offset));
            this.assertApproach(field, new BlockPos(30, 64, offset), new BlockPos(18, 64, offset));
        }
    }

    @Test
    void obstructionRoutesToNearbyAccessibleWallInsteadOfCorner() {
        FlowField field = FlowField.computeTestField(this.geo,
            (x, z) -> x >= -2 && x <= 2 && z >= -23 && z <= -19, (x, z) -> false);
        BlockPos current = new BlockPos(0, 64, -29);
        FlowField.Step step = null;
        for (int i = 0; i < 30; i++) {
            step = field.next(current.getX(), current.getZ(), 1);
            assertNotNull(step);
            current = step.waypoint();
            assertFalse(current.getX() >= -2 && current.getX() <= 2 && current.getZ() >= -23 && current.getZ() <= -19);
            if (step.atWall()) {
                break;
            }
        }

        assertNotNull(step);
        assertTrue(step.atWall());
        assertEquals(-19, current.getZ());
        assertTrue(Math.abs(current.getX()) >= 3 && Math.abs(current.getX()) <= 5);
    }

    private void assertApproach(FlowField field, BlockPos from, BlockPos expected) {
        BlockPos current = from;
        for (int i = 0; i < 10; i++) {
            FlowField.Step step = field.next(current.getX(), current.getZ(), 6);
            assertNotNull(step);
            current = step.waypoint();
            if (step.atWall()) {
                break;
            }
        }

        assertEquals(expected, current);
        for (int i = 0; i < 20; i++) {
            FlowField.Step step = field.next(current.getX(), current.getZ(), 6);
            assertNotNull(step);
            assertTrue(step.atWall());
            assertEquals(expected, step.waypoint(), "Reached wall target must remain stable");
        }
    }
}
