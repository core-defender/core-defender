package lkd.namsic.coredefender.wall;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.wave.WaveMobs;
import lkd.namsic.coredefender.wave.WaveProjectiles;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("coredefender")
@PrefixGameTestTemplate(false)
// GameTest discovers this regression through its annotation.
@SuppressWarnings("unused")
public class BarrierProjectileGameTests {
    @GameTest(template = "empty", batch = "orphanedWaveArrow")
    public void arrowStillHitsWallAfterShooterRemovalAndEntityReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WallState previous = WallManager.state(level);
        Difficulty previousDifficulty = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.NORMAL, true);
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        WallState wall = new WallState();
        wall.built = true;
        wall.centerX = origin.getX() + 32;
        wall.centerZ = origin.getZ();
        wall.baseY = origin.getY();
        wall.resetSegments();
        level.getDataStorage().set(WallState.ID, wall);
        Skeleton shooter = helper.spawn(EntityType.SKELETON, new BlockPos(2, 1, 2));
        shooter.addTag(WaveMobs.TAG);
        shooter.getPersistentData().putDouble("cd_wave_damage_scale", 2);
        Arrow arrow = new Arrow(level, origin.getX() + 10, origin.getY() + 2, origin.getZ() + 4, new ItemStack(Items.ARROW), null);
        Arrow restored = new Arrow(EntityType.ARROW, level);
        Husk ally = helper.spawn(EntityType.HUSK, new BlockPos(3, 1, 3));
        ally.addTag(WaveMobs.TAG);
        try {
            arrow.setOwner(shooter);
            arrow.setDeltaMovement(new Vec3(8, 0, 0));
            level.addFreshEntity(arrow);
            helper.assertTrue(WaveProjectiles.isHostile(arrow), "Join event must remember the enemy shooter");
            CompoundTag saved = new CompoundTag();
            arrow.saveWithoutId(saved);
            shooter.discard();
            arrow.discard();
            restored.load(saved);
            helper.assertTrue(restored.getOwner() == null, "Regression requires an unavailable shooter");
            helper.assertTrue(WaveProjectiles.isHostile(restored) && WaveProjectiles.damageScale(restored) == 2,
                "Enemy allegiance and damage scaling must survive owner removal and NBT reload");
            float health = ally.getHealth();
            ally.hurt(level.damageSources().arrow(restored, null), 10);
            helper.assertTrue(ally.getHealth() == health, "Orphaned enemy arrows must not cause friendly fire");
            int before = wall.segments().values().stream().mapToInt(WallSegment::hp).sum();
            helper.assertTrue(BarrierSystem.interceptProjectile(level, restored), "Orphaned arrow must be intercepted before entering");
            int after = wall.segments().values().stream().mapToInt(WallSegment::hp).sum();
            helper.assertTrue(before - after == Balance.RANGED_WALL_DAMAGE * 2 && restored.isRemoved(),
                "The wall must take the original scaled damage and remove the arrow");
            helper.succeed();
        } finally {
            shooter.discard();
            arrow.discard();
            restored.discard();
            ally.discard();
            level.getDataStorage().set(WallState.ID, previous);
            level.getServer().setDifficulty(previousDifficulty, true);
        }
    }
}

