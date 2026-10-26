package lkd.namsic.coredefender.wall;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class BarrierGeometryTest {
    @Test
    void newBarrierExactlyMatchesChunkSizeWithoutLegacyWallThickness() {
        WallState state = new WallState();
        for (int half : new int[]{1, 2, 4}) {
            state.half = half;
            WallGeometry boundary = state.geometry();
            assertEquals(half * 32, boundary.outerMaxX() + 1 - boundary.outerMinX());
            assertEquals(half * 32, boundary.outerMaxZ() + 1 - boundary.outerMinZ());
            AABB west = state.panels().stream().filter(p -> p.id().equals("W0")).findFirst().orElseThrow().box();
            assertEquals(-half * 16, (west.minX + west.maxX) / 2, 1e-6);
        }
    }

    private final WallGeometry geo = new WallGeometry(0, 0, 64, 1, 5, 2);
    private final List<BarrierGeometry.Panel> panels = BarrierGeometry.panels(this.geo);
    private final List<VoxelShape> shapes = this.panels.stream().map(BarrierGeometry.Panel::shape).toList();

    @Test
    void panelIdsCoverEverySavedSegment() {
        assertEquals(this.geo.segmentIds().stream().sorted().toList(), this.panels.stream().map(BarrierGeometry.Panel::id).sorted().toList());
    }

    @Test
    void fastMovementCannotTunnelThroughAnySide() {
        for (int side = 0; side < 4; side++) {
            double x = side == 0 ? -25 : side == 1 ? 25 : 4;
            double z = side == 2 ? -25 : side == 3 ? 25 : 4;
            AABB box = new AABB(x - .3, 64, z - .3, x + .3, 65.8, z + .3);
            Vec3 motion = new Vec3(side == 0 ? 60 : side == 1 ? -60 : 0, 0, side == 2 ? 60 : side == 3 ? -60 : 0);
            Vec3 clipped = BarrierGeometry.clipMovement(box, motion, this.shapes);
            assertTrue(clipped.length() < 7, "side " + side);
            assertTrue(clipped.length() > 6);
        }
    }

    @Test
    void wallAllowsSlidingAndDoesNotTeleport() {
        AABB box = new AABB(-19, 64, 1, -18.4, 65.8, 1.6);
        Vec3 clipped = BarrierGeometry.clipMovement(box, new Vec3(2, 0, 3), this.shapes);
        assertEquals(.38, clipped.x, 1e-6);
        assertEquals(3, clipped.z, 1e-6);
        assertEquals(0, clipped.y);
    }

    @Test
    void diagonalCornerDoesNotEnterInterior() {
        AABB box = new AABB(-25, 64, -25, -24.4, 65.8, -24.4);
        Vec3 clipped = BarrierGeometry.clipMovement(box, new Vec3(20, 0, 20), this.shapes);
        AABB moved = box.move(clipped);
        assertTrue(moved.maxX <= -18 || moved.maxZ <= -18);
    }

    @Test
    void aboveAndBelowBarrierRemainOpen() {
        for (double y : new double[]{60, 70}) {
            Vec3 requested = new Vec3(40, 0, 0);
            AABB box = new AABB(-25, y, 1, -24.4, y + 1.8, 1.6);
            assertEquals(requested, BarrierGeometry.clipMovement(box, requested, this.shapes));
        }
    }

    @Test
    void collapsedPanelIsOpenAndRepairClosesIt() {
        List<VoxelShape> open = this.panels.stream().filter(p -> !p.id().equals("W1")).map(BarrierGeometry.Panel::shape).toList();
        AABB box = new AABB(-25, 64, 3, -24.4, 65.8, 3.6);
        Vec3 motion = new Vec3(15, 0, 0);
        assertEquals(motion, BarrierGeometry.clipMovement(box, motion, open));
        assertTrue(BarrierGeometry.clipMovement(box, motion, this.shapes).x < 7);
    }

    @Test
    void fastProjectileIntersectsThinPanelAndHasCorrectHeight() {
        BarrierGeometry.Panel west = this.panels.stream().filter(p -> p.id().equals("W1")).findFirst().orElseThrow();
        Vec3 from = new Vec3(-40, 65, 4), to = new Vec3(40, 65, 4);
        Vec3 hit = west.box().clip(from, to).orElseThrow();
        assertEquals(-18.02, hit.x, 1e-6);
        assertTrue(west.box().clip(new Vec3(-40, 70, 4), new Vec3(40, 70, 4)).isEmpty());
    }

    @Test
    void expandedGeometryMovesBoundaryAndAddsSegments() {
        List<BarrierGeometry.Panel> expanded = BarrierGeometry.panels(new WallGeometry(0, 0, 64, 2, 5, 2));
        assertEquals(16, expanded.size());
        assertEquals(-34.02, expanded.stream().filter(p -> p.id().equals("W0")).findFirst().orElseThrow().box().minX, 1e-6);
    }

    @Test
    void traceSkipsCollapsedSegmentsAndUsesNearestActiveBoundary() {
        WallState state = new WallState();
        state.built = true;
        state.baseY = 64;
        state.resetSegments();
        Vec3 from = new Vec3(-40, 65, 4), to = new Vec3(40, 65, 4);
        assertEquals("W1", Objects.requireNonNull(BarrierSystem.trace(state, from, to)).segment());
        Objects.requireNonNull(state.segments.get("W1")).collapsed = true;
        assertEquals("E1", Objects.requireNonNull(BarrierSystem.trace(state, from, to)).segment());
        Objects.requireNonNull(state.segments.get("E1")).collapsed = true;
        assertNull(BarrierSystem.trace(state, from, to));
        Objects.requireNonNull(state.segments.get("W1")).collapsed = false;
        assertEquals("W1", Objects.requireNonNull(BarrierSystem.trace(state, from, to)).segment());
    }

    @Test
    void saveRetainsUpgradeHpAndMigrationMarker() {
        WallState state = new WallState();
        state.built = true;
        state.baseY = 64;
        state.half = 2;
        state.largestHalf = 3;
        state.sideLevels[0] = 2;
        state.resetSegments();
        Objects.requireNonNull(state.segments.get("N0")).hp = 123;
        CompoundTag tag = state.save(new CompoundTag(), RegistryAccess.EMPTY);
        WallState loaded = WallState.load(tag, RegistryAccess.EMPTY);
        assertTrue(loaded.barrierMigrated);
        assertEquals(123, Objects.requireNonNull(loaded.segments.get("N0")).hp);
        assertEquals(1000, Objects.requireNonNull(loaded.segments.get("N0")).maxHp);
        assertEquals(2, loaded.sideLevel("N"));
        assertEquals(3, loaded.largestHalf);
        tag.remove("barrierMigrated");
        assertFalse(WallState.load(tag, RegistryAccess.EMPTY).barrierMigrated, "Old saves require one-time conversion");
    }
}
