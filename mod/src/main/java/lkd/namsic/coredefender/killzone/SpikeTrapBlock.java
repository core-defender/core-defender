package lkd.namsic.coredefender.killzone;

import com.mojang.serialization.MapCodec;
import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.wave.WaveMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 킬존 가시 함정: 지나갈 수 있지만 웨이브 몹을 느리게 하고 피해를 준다.
 */
public class SpikeTrapBlock extends Block {
    public static final MapCodec<SpikeTrapBlock> CODEC = simpleCodec(SpikeTrapBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 3, 16);
    private static final Vec3 SLOW = new Vec3(0.6, 1.0, 0.6);

    public SpikeTrapBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof LivingEntity living) || !WaveMobs.isWaveMob(living)) {
            return;
        }

        entity.makeStuckInBlock(state, SLOW);
        if (level.getGameTime() % Balance.SPIKE_INTERVAL == 0) {
            living.hurt(level.damageSources().cactus(), Balance.SPIKE_DAMAGE);
        }
    }
}
