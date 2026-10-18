package lkd.namsic.coredefender.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class WallBlock extends Block {
    public static final MapCodec<WallBlock> CODEC = simpleCodec(WallBlock::new);
    /** 구간이 붕괴했는지 (외형만 바뀜) */
    public static final BooleanProperty BROKEN = BooleanProperty.create("broken");

    public WallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BROKEN, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BROKEN);
    }
}
