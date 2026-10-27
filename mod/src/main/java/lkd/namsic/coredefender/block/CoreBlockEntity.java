package lkd.namsic.coredefender.block;

import lkd.namsic.coredefender.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 상태 없이 떠서 회전하는 보석을 렌더링하기 위한 블록 엔티티.
 */
public class CoreBlockEntity extends BlockEntity {
    public CoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CORE.get(), pos, state);
    }
}
