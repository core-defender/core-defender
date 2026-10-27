package lkd.namsic.coredefender.block;

import com.mojang.serialization.MapCodec;
import dev.latvian.mods.kubejs.script.ScriptType;
import lkd.namsic.coredefender.kubejs.CoreDefenderKubeEvents;
import lkd.namsic.coredefender.kubejs.MenuActionKubeEvent;
import lkd.namsic.coredefender.wall.CoreStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CoreBlock extends Block implements EntityBlock {
    public static final MapCodec<CoreBlock> CODEC = simpleCodec(CoreBlock::new);

    public CoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoreBlockEntity(pos, state);
    }

    /**
     * 우클릭 → K 키와 같은 관리 메뉴 (메뉴 내용은 KubeJS 가 만든다). Shift+우클릭 → 코어 창고
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                CoreStorage.open(serverPlayer);
            } else {
                CoreDefenderKubeEvents.MENU_ACTION.post(ScriptType.SERVER, new MenuActionKubeEvent(serverPlayer, "open", "core"));
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
