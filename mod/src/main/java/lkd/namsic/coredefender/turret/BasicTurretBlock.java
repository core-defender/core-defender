package lkd.namsic.coredefender.turret;

import lkd.namsic.coredefender.client.TurretRangeRenderer;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.mojang.serialization.MapCodec;
import lkd.namsic.coredefender.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.util.List;

public class BasicTurretBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE=Shapes.or(Block.box(1,0,1,15,3,15),Block.box(3,3,3,13,11,13),Block.box(2,11,2,14,12,14),Block.box(5,12,5,11,16,11));
    public static final MapCodec<BasicTurretBlock> CODEC=simpleCodec(BasicTurretBlock::new);
    public BasicTurretBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new BasicTurretBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ModBlockEntities.BASIC_TURRET.get(),BasicTurretBlockEntity::serverTick);}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(stack.is(ModItems.RANGE_ANALYZER.get())) {
            if(level.isClientSide)TurretRangeRenderer.show(pos);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if(player.isShiftKeyDown()) {
            if(!level.isClientSide && level.getBlockEntity(pos) instanceof BasicTurretBlockEntity turret) {
                ItemStack remainder=ItemHandlerHelper.insertItemStacked(turret.inventory,stack.copy(),false);
                if(!player.isCreative())stack.setCount(remainder.getCount());
                player.displayClientMessage(Component.literal("포탑에 자원을 보충했습니다."),true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        if(player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof BasicTurretBlockEntity turret)server.openMenu(turret,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder) {
        ItemStack stack=new ItemStack(this);
        if(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BasicTurretBlockEntity turret)stack.set(DataComponents.CUSTOM_DATA,CustomData.of(turret.portableData()));
        return List.of(stack);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if(!level.isClientSide && data != null && level.getBlockEntity(pos) instanceof BasicTurretBlockEntity turret)turret.restore(data.copyTag());
    }
}
