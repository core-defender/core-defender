package lkd.namsic.coredefender.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lkd.namsic.coredefender.wall.WallManager;
import lkd.namsic.coredefender.wall.WallState;
import lkd.namsic.coredefender.wall.WallGeometry;
import lkd.namsic.coredefender.wall.WallSegment;
import net.minecraft.nbt.CompoundTag;
import lkd.namsic.coredefender.wave.WaveMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("coredefender")
@PrefixGameTestTemplate(false)
// GameTest discovers this entry point through annotations.
@SuppressWarnings("unused")
public class WallGameTests {
    @GameTest(template = "empty", batch = "creeperWallProtection")
    public void creeperExplosionsProtectInteriorFloorWithoutBlockingWallDamage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WallState previous = WallManager.state(level);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        CompoundTag saved = previous.save(new CompoundTag(), level.registryAccess());
        saved.putBoolean("built", true);
        saved.putInt("centerX", origin.getX() + 16);
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
        WallState state = WallState.FACTORY.deserializer().apply(saved, level.registryAccess());
        level.getDataStorage().set(WallState.ID, state);
        WallGeometry geo = state.geometry();
        BlockPos floor = new BlockPos(geo.minX(), geo.baseY() - 1, geo.centerZ());
        BlockState original = level.getBlockState(floor);
        try {
            for (boolean tagged : new boolean[]{false, true}) {
                Creeper creeper = Objects.requireNonNull(EntityType.CREEPER.create(level));
                if (tagged) {
                    creeper.addTag(WaveMobs.TAG);
                }

                level.setBlockAndUpdate(floor, Blocks.OAK_PLANKS.defaultBlockState());
                int health = state.segments().values().stream().mapToInt(WallSegment::hp).sum();
                Explosion explosion = new Explosion(level, creeper, geo.outerMinX() - .5, geo.baseY() - .5,
                    geo.centerZ() + .5, 3, false, Explosion.BlockInteraction.DESTROY, new ArrayList<>(List.of(floor)));
                ProtectionEvents.protectExplosion(level, geo, new ExplosionEvent.Detonate(level, explosion, new ArrayList<>()));
                helper.assertTrue(explosion.getToBlow().isEmpty(), "Interior floor must be removed from creeper destruction, tagged=" + tagged);
                explosion.finalizeExplosion(false);
                helper.assertTrue(level.getBlockState(floor).is(Blocks.OAK_PLANKS), "Explosion finalization must preserve the floor");
                helper.assertTrue(state.segments().values().stream().mapToInt(WallSegment::hp).sum() < health,
                    "Protecting blocks must not cancel wall HP damage");
                WallManager.repair(level, null, 0);
            }
        } finally {
            level.setBlockAndUpdate(floor, original);
            level.getDataStorage().set(WallState.ID, previous);
        }

        helper.succeed();
    }
}
