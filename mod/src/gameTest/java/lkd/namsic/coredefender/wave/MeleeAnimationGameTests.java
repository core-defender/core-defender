package lkd.namsic.coredefender.wave;

import lkd.namsic.coredefender.wall.WallGeometry;
import lkd.namsic.coredefender.wall.WallManager;
import lkd.namsic.coredefender.wall.WallState;
import lkd.namsic.coredefender.wall.WallSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.warden.Warden;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;

@GameTestHolder("coredefender")
@PrefixGameTestTemplate(false)
// GameTest invokes these entry points through annotation discovery.
@SuppressWarnings("unused")
public class MeleeAnimationGameTests {
    @GameTest(template = "empty", batch = "wallMeleeAnimation")
    public void ironGolemWallStrikeStartsNativeAnimationWithoutHittingOtherMobs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        WallState previous = WallManager.state(level);
        CompoundTag saved = previous.save(new CompoundTag(), level.registryAccess());
        saved.putBoolean("built", true);
        saved.putInt("centerX", origin.getX() + 19);
        saved.putInt("centerZ", origin.getZ());
        saved.putInt("baseY", origin.getY());
        saved.putInt("half", 1);
        saved.putInt("height", 5);
        saved.putInt("thickness", 0);
        CompoundTag segments = new CompoundTag();
        for (String side : WallGeometry.SIDES) {
            for (int i = 0; i < 2; i++) {
                CompoundTag segment = new CompoundTag();
                segment.putInt("hp", 500);
                segment.putInt("maxHp", 500);
                segments.put(side + i, segment);
            }
        }

        saved.put("segments", segments);
        WallState wall = WallState.FACTORY.deserializer().apply(saved, level.registryAccess());
        level.getDataStorage().set(WallState.ID, wall);
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 1, 2));
        golem.setNoAi(true);
        Cow nearby = helper.spawn(EntityType.COW, new BlockPos(1, 1, 2));
        nearby.setNoAi(true);
        try {
            WaveMobController.MobState state = new WaveMobController.MobState();
            FlowField field = FlowField.computeTestField(wall.geometry(), (x, z) -> false, (x, z) -> false);
            WaveMobController.tick(level, golem, state, wall.geometry(), field);
            helper.assertTrue(golem.getAttackAnimationTick() == 10, "A wall strike must start the iron golem arm animation");
            int afterHit = wall.segments().values().stream().mapToInt(WallSegment::hp).sum();
            helper.assertTrue(afterHit < 4000, "The animated strike must still damage the wall");
            WaveMobController.tick(level, golem, state, wall.geometry(), field);
            helper.assertTrue(wall.segments().values().stream().mapToInt(WallSegment::hp).sum() == afterHit, "Animation must not bypass attack cooldown");
            helper.assertTrue(nearby.getHealth() == nearby.getMaxHealth() && golem.getTarget() == null, "Wall animation must not attack or target another mob");
            helper.succeed();
        } finally {
            golem.discard();
            nearby.discard();
            level.getDataStorage().set(WallState.ID, previous);
        }
    }

    @GameTest(template = "empty", batch = "nativeMeleeAnimations")
    public void specialMobsUseTheirNativeAnimationsAndHumanoidsStillSwing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Ravager ravager = Objects.requireNonNull(EntityType.RAVAGER.create(level));
        Hoglin hoglin = Objects.requireNonNull(EntityType.HOGLIN.create(level));
        Zoglin zoglin = Objects.requireNonNull(EntityType.ZOGLIN.create(level));
        Warden warden = Objects.requireNonNull(EntityType.WARDEN.create(level));
        Husk husk = Objects.requireNonNull(EntityType.HUSK.create(level));
        WaveMobController.playMeleeAttack(level, ravager);
        WaveMobController.playMeleeAttack(level, hoglin);
        WaveMobController.playMeleeAttack(level, zoglin);
        WaveMobController.playMeleeAttack(level, warden);
        WaveMobController.playMeleeAttack(level, husk);
        helper.assertTrue(ravager.getAttackTick() == 10, "Ravager attack must animate");
        helper.assertTrue(hoglin.getAttackAnimationRemainingTicks() == 10 && zoglin.getAttackAnimationRemainingTicks() == 10, "Hoglin and zoglin attacks must animate");
        helper.assertTrue(warden.attackAnimationState.isStarted(), "Warden attack must animate");
        helper.assertTrue(husk.swinging, "Generic arm-swing behavior must be preserved");
        helper.succeed();
    }
}
