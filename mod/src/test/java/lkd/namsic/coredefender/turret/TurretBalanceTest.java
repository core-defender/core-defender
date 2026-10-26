package lkd.namsic.coredefender.turret;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TurretBalanceTest {
    private TurretStats stats(int kind, double damage, double speed, double range, double flameRange, double angle) {
        return new TurretStats(kind, damage, speed, 0, 0, 0, 0, range, 0, 0, flameRange, angle, 0);
    }

    @Test
    void baseCyclesAndResourcesMatchProposal() {
        TurretStats arrow = this.stats(0, 0, 0, 0, 0, 0);
        TurretStats stone = this.stats(1, 0, 0, 0, 0, 0);
        TurretStats flame = this.stats(2, 0, 0, 0, 0, 0);
        assertEquals(5, arrow.damage(false, 0));
        assertEquals(24, arrow.range(false, 0));
        assertEquals(16, arrow.interval(0));
        assertEquals(9, stone.damage(false, 0));
        assertEquals(13, stone.damage(true, 0));
        assertEquals(60, stone.interval(0));
        assertEquals(36, stone.range(false, 0));
        assertEquals(32, stone.range(true, 0));
        assertEquals(3, stone.radius(false));
        assertEquals(4, stone.radius(true));
        assertEquals(3, flame.damage(false, 0));
        assertEquals(5, flame.range(false, 0));
        assertEquals(30, flame.interval(0));

    }

    @Test
    void oilUsesFlameRangeCycleAndConsumption() {
        for (double bonus : new double[]{0, .3, .6}) {
            TurretStats flame = this.stats(TurretStats.FLAME, bonus, bonus, 0, bonus * 5, bonus * 450);
            TurretStats oil = this.stats(TurretStats.OIL, bonus, bonus, 0, bonus * 5, bonus * 450);
            assertEquals(flame.damage(false, 0), oil.damage(false, 0));
            assertEquals(flame.range(false, 0), oil.range(false, 0));
            assertEquals(flame.interval(0), oil.interval(0));
            assertEquals(oil.range(false, 0) * 2, oil.detectionRange());
        }

        assertEquals(10 * TurretStats.FLAME_OIL_PER_TICK, TurretStats.OIL_PER_POUR);
    }

    @Test
    void duplicateGradesAndAmplifiersAddRatherThanMultiply() {
        TurretStats stone = this.stats(1, .9, .6, .9, 0, 0);
        assertEquals(9 * 2.15, stone.damage(false, 2), 1e-9);
        assertEquals(36 * 2.1, stone.range(false, 2), 1e-9);
        assertEquals(33, stone.interval(1));
        TurretStats flame = this.stats(2, .9, .6, 0, 3, 270);
        assertEquals(360, flame.angle());
        assertEquals(8, flame.range(false, 3));
        assertEquals(23, flame.interval(3));
        assertEquals(3 * 2.2, flame.damage(false, 3), 1e-9);
    }

    @Test
    void boostedStoneRangeRemainsBallisticallyReachable() {
        for (double rangeBonus : new double[]{0, .15, .45, .9}) {
            for (int amplifier : new int[]{0, 2}) {
                TurretStats stats = this.stats(1, 0, 0, rangeBonus, 0, 0);
                TurretCombat.Trajectory shot = TurretCombat.trajectory(new Vec3(0, 1.3, 0), new Vec3(stats.range(false, amplifier) - .5, .9, 0), Vec3.ZERO, stats.projectileSpeed(amplifier), .05);
                assertNotNull(shot, "range=" + stats.range(false, amplifier));
            }
        }
    }

    @Test
    void movingTargetsAreLedAndFlightTimeDependsOnDistance() {
        TurretCombat.Trajectory close = TurretCombat.trajectory(Vec3.ZERO, new Vec3(8, 0, 0), Vec3.ZERO, 2.4, .05);
        TurretCombat.Trajectory far = TurretCombat.trajectory(Vec3.ZERO, new Vec3(16, 0, 0), Vec3.ZERO, 2.4, .05);
        TurretCombat.Trajectory moving = TurretCombat.trajectory(Vec3.ZERO, new Vec3(16, 0, 0), new Vec3(0, 0, .2), 2.4, .05);
        assertNotNull(close);
        assertNotNull(far);
        assertNotNull(moving);
        assertTrue(far.ticks() > close.ticks());
        assertTrue(moving.velocity().z() > 0);
    }

    @Test
    void flameConeHonorsAngleHeightAndReach() {
        assertTrue(TurretCombat.inCone(Vec3.ZERO, new Vec3(0, 0, 4), 0, 4, 90));
        assertFalse(TurretCombat.inCone(Vec3.ZERO, new Vec3(4, 0, 0), 0, 4, 90));
        assertTrue(TurretCombat.inCone(Vec3.ZERO, new Vec3(0, 0, -4), 0, 4, 360));
        assertFalse(TurretCombat.inCone(Vec3.ZERO, new Vec3(0, 2.01, 1), 0, 4, 360));
        assertFalse(TurretCombat.inCone(Vec3.ZERO, new Vec3(0, 0, 4.01), 0, 4, 360));
    }
}
