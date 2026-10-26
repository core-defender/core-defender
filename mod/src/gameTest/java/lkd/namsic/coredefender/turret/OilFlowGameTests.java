package lkd.namsic.coredefender.turret;

import lkd.namsic.coredefender.wall.WallGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("coredefender")
@PrefixGameTestTemplate(false)
@SuppressWarnings("unused")
public class OilFlowGameTests {
    @GameTest(template = "empty")
    public void flowSpreadsSidewaysAndFindsDropWithoutClimbing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(1, 2, 1));
        WallGeometry wall = new WallGeometry(base.getX() + 100, base.getZ(), base.getY(), 1, 4, 0);
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                level.setBlockAndUpdate(base.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(base.offset(x, 1, z), Blocks.AIR.defaultBlockState());
            }
        }

        Vec3 source = Vec3.atBottomCenterOf(base.offset(2, 1, 2));
        helper.assertTrue(OilSpill.flowCandidates(level, wall, source).size() == 4,
            "Flat ground must permit all four directions, including sideways and behind the pour");
        level.setBlockAndUpdate(base.offset(3, 1, 2), Blocks.STONE.defaultBlockState());
        helper.assertTrue(OilSpill.flowCandidates(level, wall, source).size() == 3,
            "Oil must not climb a solid step");
        level.setBlockAndUpdate(base.offset(2, 0, 4), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(base.offset(2, -1, 4), Blocks.STONE.defaultBlockState());
        helper.assertTrue(OilSpill.slopeDistance(level, wall, source.add(0, 0, 1), source, 2) == 1,
            "Flow must find the nearby downward route rather than filling a cone");
        WallGeometry inside = new WallGeometry(base.getX(), base.getZ(), base.getY(), 1, 4, 0);
        helper.assertTrue(OilSpill.flowCandidates(level, inside, source).isEmpty(),
            "Flow must never enter the wall interior");
        helper.succeed();
    }
}
