package lkd.namsic.coredefender.turret;

import lkd.namsic.coredefender.registry.ModBlocks;
import lkd.namsic.coredefender.wall.WallGeometry;
import lkd.namsic.coredefender.wall.WallManager;
import lkd.namsic.coredefender.wall.WallState;
import lkd.namsic.coredefender.wave.WaveMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;

@GameTestHolder("coredefender")
@PrefixGameTestTemplate(false)
// Discovered by the GameTest annotation scanner.
@SuppressWarnings("unused")
public class OilTurretGameTests {
    private Husk victim(GameTestHelper helper, int x, int y) {
        Husk mob = helper.spawn(EntityType.HUSK, new BlockPos(x, y, 2));
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.addTag(WaveMobs.TAG);
        return mob;
    }

    private WallGeometry wall(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        return new WallGeometry(origin.getX() - 14, origin.getZ(), origin.getY() + 1, 1, 5, 0);
    }

    @GameTest(template = "empty", batch = "oilTerrain", timeoutTicks = 260)
    public void oilFlowsDownButNotUphillOrInsideAndExpires(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        for (int x = 0; x <= 7; x++) {
            helper.setBlock(new BlockPos(x, 0, 2), Blocks.STONE);
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }

        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 1, 2), Blocks.STONE);
        WallGeometry wall = this.wall(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 2));
        BasicTurretBlockEntity turret = new BasicTurretBlockEntity(pos, ModBlocks.OIL_TURRET.get().defaultBlockState());
        OilSpill spill = new OilSpill();
        Vec3 source = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        spill.pour(source, world.getGameTime(), turret.stats(), 0);
        Husk inside = this.victim(helper, 1, 1);
        Husk downhill = this.victim(helper, 4, 1);
        Objects.requireNonNull(downhill.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(200);
        downhill.setHealth(200);
        Husk uphill = this.victim(helper, 5, 2);
        Cow friendly = helper.spawn(EntityType.COW, new BlockPos(4, 1, 2));
        friendly.setNoAi(true);
        friendly.setNoGravity(true);
        helper.onEachTick(() -> spill.tick(world, wall, turret));
        helper.runAfterDelay(1, () -> helper.assertTrue(downhill.getHealth() == 200, "Oil must take time to reach a distant floor cell"));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(downhill.getHealth() < 200, "Oil must flow down to a lower surface");
            helper.assertTrue(inside.getHealth() == 20, "Oil must never flow into the wall interior, even downhill");
            helper.assertTrue(uphill.getHealth() == 20, "Oil must not climb a block");
            helper.assertTrue(friendly.getHealth() == friendly.getMaxHealth(), "Oil must not hurt a non-enemy on the same surface");
            helper.assertTrue(downhill.getDeltaMovement().equals(Vec3.ZERO), "Oil damage must not cause knockback");
        });
        helper.runAfterDelay(210, () -> downhill.setHealth(200));
        helper.runAfterDelay(230, () -> {
            helper.assertTrue(downhill.getHealth() == 200, "Oil and its residual burn must both expire");
            helper.assertTrue(world.getBlockState(BlockPos.containing(source)).isAir(), "Oil must not place fluid or fire blocks");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "oilPlacement")
    public void oilPlacementResetsDirectionAndRetainsItWhenLoaded(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        world.setBlockAndUpdate(pos, ModBlocks.OIL_TURRET.get().defaultBlockState());
        BasicTurretBlockEntity turret = (BasicTurretBlockEntity) Objects.requireNonNull(world.getBlockEntity(pos));
        Cow placer = Objects.requireNonNull(EntityType.COW.create(world));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            turret.orientOil(direction.getOpposite());
            ItemStack item = new ItemStack(ModBlocks.OIL_TURRET.get());
            item.set(DataComponents.CUSTOM_DATA, CustomData.of(turret.portableData()));
            placer.setYRot(direction.toYRot());
            ModBlocks.OIL_TURRET.get().setPlacedBy(world, pos, turret.getBlockState(), placer, item);
            float expected = Mth.wrapDegrees(direction.toYRot());
            helper.assertTrue(turret.portableData().getFloat("yaw") == expected, "Reinstallation must use the new placement direction");
            BasicTurretBlockEntity restored = new BasicTurretBlockEntity(pos, turret.getBlockState());
            restored.setLevel(world);
            restored.loadAdditional(turret.portableData(), world.registryAccess());
            helper.assertTrue(restored.portableData().getFloat("yaw") == expected, "Saved direction must survive loading");
        }

        helper.succeed();
    }

    @GameTest(template = "empty", batch = "oilFlameDamageParity")
    public void oilMatchesFlameFourHitBurstLavaAndResidualBurn(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        helper.setBlock(new BlockPos(2, 0, 2), Blocks.STONE);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 2));
        BasicTurretBlockEntity turret = new BasicTurretBlockEntity(pos, ModBlocks.OIL_TURRET.get().defaultBlockState());
        OilSpill spill = new OilSpill();
        Vec3 source = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)));
        spill.pour(source, world.getGameTime(), turret.stats(), 3);
        Husk target = this.victim(helper, 2, 1);
        Objects.requireNonNull(target.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(100);
        Objects.requireNonNull(target.getAttribute(Attributes.ARMOR)).setBaseValue(0);
        target.setHealth(100);
        WallGeometry wall = this.wall(helper);
        helper.onEachTick(() -> spill.tick(world, wall, turret));
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(Math.abs(target.getHealth() - 84.4F) < .01, "Four amplified hits must deal 4 x 3.9 damage, actual=" + target.getHealth());
            helper.assertTrue(target.getDeltaMovement().equals(Vec3.ZERO), "Oil burst must not knock back");
        });
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(Math.abs(target.getHealth() - 83.1F) < .01, "Rest period must contain only the 1.3 amplified burn tick, actual=" + target.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "oilDetection")
    public void fixedOilTurretDetectsEarlyAndConsumesFlameBudgets(GameTestHelper helper) {
        ServerLevel world = helper.getLevel();
        for (int x = 0; x <= 11; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }

        WallGeometry geometry = this.wall(helper);
        WallState previous = WallManager.state(world);
        CompoundTag data = previous.save(new CompoundTag(), world.registryAccess());
        data.putInt("centerX", geometry.centerX());
        data.putInt("centerZ", geometry.centerZ());
        data.putInt("baseY", geometry.baseY());
        data.putInt("half", 1);
        data.putInt("thickness", 0);
        world.getDataStorage().set(WallState.ID, WallState.FACTORY.deserializer().apply(data, world.registryAccess()));
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 2));
        world.setBlockAndUpdate(pos, ModBlocks.OIL_TURRET.get().defaultBlockState());
        BasicTurretBlockEntity turret = new BasicTurretBlockEntity(pos, ModBlocks.OIL_TURRET.get().defaultBlockState()) {
            @Override
            protected boolean operatingArea(ServerLevel level) {
                return true;
            }
        };
        world.setBlockEntity(turret);
        turret.orientOil(Direction.EAST);
        // The isolated test server lacks Create Additions; bypass only the oil type validator.
        turret.oil.setFluid(new FluidStack(Fluids.WATER, TurretStats.OIL_PER_POUR));
        turret.faces[Direction.UP.ordinal()] = 1;
        helper.assertTrue(turret.fluidPort(Direction.UP).fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000, "Oil turret must accept the same lava amplifier as flame");
        Husk distant = this.victim(helper, 10, 1);
        boolean[] early = {false};
        helper.runAfterDelay(25, () -> {
            early[0] = turret.oil.isEmpty() && turret.getLastShot() > 0 && distant.getHealth() == 20 && turret.status == 4;
            turret.oil.setFluid(new FluidStack(Fluids.WATER, TurretStats.OIL_PER_POUR));
            distant.moveTo(pos.getX() + 1.3, pos.getY(), pos.getZ() + .5);
        });
        helper.runAfterDelay(50, () -> {
            try {
                helper.assertTrue(turret.lava.getFluidAmount() == 980, "Two pours must consume 20mB lava");
                helper.assertTrue(early[0], "Distant detection must consume oil, display shortage and cause no instant ranged damage");
                helper.assertTrue(turret.oil.isEmpty() && world.getGameTime() - turret.getLastShot() < 30, "Enemies touching the barrier must still trigger a second pour");
                helper.assertTrue(turret.portableData().getFloat("yaw") == -90, "Oil turret must not track targets with its head");
                helper.succeed();
            } finally {
                world.removeBlock(pos, false);
                world.getDataStorage().set(WallState.ID, previous);
            }
        });
    }
}
