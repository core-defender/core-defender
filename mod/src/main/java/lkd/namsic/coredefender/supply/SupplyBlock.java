package lkd.namsic.coredefender.supply;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lkd.namsic.coredefender.registry.ModBlockEntities;
import lkd.namsic.coredefender.registry.ModItems;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * 보급 송신기/수신기. 강화 부품을 들고 우클릭하면 다음 단계로 강화된다 (단계 순서대로만).
 */
public class SupplyBlock extends BaseEntityBlock {
    public static final MapCodec<SupplyBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        propertiesCodec(),
        Codec.BOOL.fieldOf("sender").forGetter(SupplyBlock::isSender)
    ).apply(instance, SupplyBlock::new));

    @Getter
    private final boolean sender;

    public SupplyBlock(Properties properties, boolean sender) {
        super(properties);
        this.sender = sender;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SupplyBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.SUPPLY.get(), SupplyBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        int upgradeTier = ModItems.supplyUpgradeTier(stack);
        if (upgradeTier < 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SupplyBlockEntity supply) {
            if (upgradeTier != supply.getTier() + 1) {
                player.displayClientMessage(Component.translatable("message.coredefender.supply_upgrade_order", supply.getTier() + 1), true);
            } else if (supply.upgrade()) {
                stack.consume(1, player);
                player.displayClientMessage(Component.translatable("message.coredefender.supply_upgraded", supply.getTier()), true);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof SupplyBlockEntity supply) {
            int buffered = SupplyNetwork.get(serverLevel.getServer()).itemCount();
            String key = supply.isOperational() ? "message.coredefender.supply_status" : "message.coredefender.supply_wrong_dimension";
            player.displayClientMessage(Component.translatable(key, supply.getTier(), buffered), true);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SupplyBlockEntity supply) {
            supply.releaseChunks();
            for (int i = 0; i < supply.getInventory().getSlots(); i++) {
                Block.popResource(level, pos, supply.getInventory().getStackInSlot(i));
            }
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
