package lkd.namsic.coredefender.killzone;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 킬존 바리케이드: 몹이 우회하고, 길이 완전히 막히면 공격하는 장애물.
 */
public class BarricadeBlock extends Block {
    public static final MapCodec<BarricadeBlock> CODEC = simpleCodec(BarricadeBlock::new);

    public BarricadeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel && !oldState.is(this)) {
            KillzoneManager.register(serverLevel, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel && !newState.is(this)) {
            KillzoneManager.unregister(serverLevel, pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
