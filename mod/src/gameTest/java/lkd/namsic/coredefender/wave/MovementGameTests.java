package lkd.namsic.coredefender.wave;

import lkd.namsic.coredefender.wall.WallGeometry;
import lkd.namsic.coredefender.wall.WallManager;
import lkd.namsic.coredefender.wall.WallState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("coredefender")
@PrefixGameTestTemplate(false)
// GameTest discovers the controller regression through annotations.
@SuppressWarnings("unused")
public class MovementGameTests {
    @GameTest(template = "empty", batch = "straightWallApproach")
    public void offCenterMobApproachesWallWithoutSidewaysDetour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }

        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0));
        WallState previous = WallManager.state(level);
        CompoundTag saved = previous.save(new CompoundTag(), level.registryAccess());
        saved.putBoolean("built", true);
        saved.putInt("centerX", origin.getX() + 22);
        saved.putInt("centerZ", origin.getZ() + 3);
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
        WallGeometry geo = wall.geometry();
        FlowField field = FlowField.computeTestField(geo, (x, z) -> false, (x, z) -> false);
        Husk mob = helper.spawn(EntityType.HUSK, new BlockPos(1, 1, 3));
        double expectedZ = origin.getZ() + 3.13;
        mob.moveTo(origin.getX() + 1.2, origin.getY(), expectedZ, -90, 0);
        WaveMobs.prepare(mob, false, false);
        WaveMobController.MobState state = new WaveMobController.MobState();
        double[] sideways = {0};
        helper.onEachTick(() -> {
            WaveMobController.tick(level, mob, state, geo, field);
            WaveMobController.tickControls(mob, state);
            sideways[0] = Math.max(sideways[0], Math.abs(mob.getZ() - expectedZ));
        });
        helper.runAfterDelay(70, () -> {
            try {
                helper.assertTrue(sideways[0] < .08, "Open approach must retain fractional Z, drift=" + sideways[0]);
                helper.assertTrue(mob.getX() >= geo.outerMinX() - 1.1, "Mob must reach the closest wall");
                helper.assertTrue(wall.segments().values().stream().anyMatch(segment -> segment.hp() < 500), "Mob must attack after reaching the wall");
                helper.succeed();
            } finally {
                mob.discard();
                level.getDataStorage().set(WallState.ID, previous);
            }
        });
    }
}
