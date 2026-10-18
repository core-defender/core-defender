package lkd.namsic.coredefender.block;

import com.mojang.serialization.MapCodec;
import dev.latvian.mods.kubejs.script.ScriptType;
import lkd.namsic.coredefender.kubejs.CoreDefenderKubeEvents;
import lkd.namsic.coredefender.kubejs.MenuActionKubeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CoreBlock extends Block {
    public static final MapCodec<CoreBlock> CODEC = simpleCodec(CoreBlock::new);

    public CoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** 우클릭 → K 키와 같은 관리 메뉴 (메뉴 내용은 KubeJS 가 만든다) */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            CoreDefenderKubeEvents.MENU_ACTION.post(ScriptType.SERVER, new MenuActionKubeEvent(serverPlayer, "open", "core"));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
