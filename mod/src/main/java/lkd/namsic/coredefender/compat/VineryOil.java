package lkd.namsic.coredefender.compat;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CoreDefender.MOD_ID)
public final class VineryOil {
    private VineryOil() {}
    public static final String KEY = "coredefender_oil";
    private static final ResourceLocation POT = ResourceLocation.parse("vinery:grapevine_pot");
    private static final TagKey<Item> SEEDS = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:seeds"));

    public static OilBatch batch(BlockEntity entity) {
        CompoundTag tag = entity.getPersistentData().getCompound(KEY);
        return new OilBatch(tag.getInt("seeds"), tag.getInt("stomps"));
    }
    public static boolean active(BlockEntity entity) { return entity != null && batch(entity).seeds() > 0; }

    private static void store(BlockEntity entity, OilBatch batch, String seed) {
        CompoundTag tag = entity.getPersistentData().getCompound(KEY);
        tag.putInt("seeds", batch.seeds());
        tag.putInt("stomps", batch.stomps());
        tag.putString("seed", seed);
        entity.getPersistentData().put(KEY, tag);
        entity.setChanged();
        if (entity.getLevel() != null) entity.getLevel().sendBlockUpdated(entity.getBlockPos(), entity.getBlockState(), entity.getBlockState(), 2);
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(POT)) return;
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null || event.getEntity().isSpectator()) return;
        ItemStack held = event.getItemStack();
        OilBatch batch = batch(entity);
        // 기존 포도가 들어 있으면 Vinery의 동작에 그대로 맡긴다.
        boolean grapes = state.getValues().entrySet().stream().anyMatch(e -> e.getKey().getName().equals("storage") && !e.getValue().equals(0));
        if (grapes || (batch.seeds() == 0 && !held.is(SEEDS))) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) return;
        Player player = event.getEntity();
        String seed = entity.getPersistentData().getCompound(KEY).getString("seed");
        if (held.is(SEEDS) && batch.seeds() < OilBatch.SEEDS) {
            String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
            if (batch.seeds() > 0 && !seed.equals(id)) {
                player.displayClientMessage(Component.translatable("message.coredefender.oil_same_seed"), true);
                return;
            }
            int count = Math.min(OilBatch.SEEDS - batch.seeds(), player.isShiftKeyDown() ? held.getCount() : 1);
            batch = batch.add(count);
            if (!player.isCreative()) held.shrink(count);
            store(entity, batch, id);
            level.playSound(null, pos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, .6F, 1F);
        } else if (held.is(Items.GLASS_BOTTLE) && batch.ready()) {
            if (!player.isCreative()) held.shrink(1);
            ItemStack output = ModItems.SEED_OIL_BOTTLE.get().getDefaultInstance();
            store(entity, new OilBatch(0, 0), "");
            if (!player.getInventory().add(output)) player.drop(output, false);
            level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1F, 1F);
            return;
        }
        player.displayClientMessage(batch.ready()
                ? Component.translatable("message.coredefender.oil_ready")
                : Component.translatable("message.coredefender.oil_progress", batch.seeds(), OilBatch.SEEDS, batch.stomps(), OilBatch.STOMPS), true);
    }

    /** 실제 착지 이벤트만 사용한다. 제자리 대기나 몹으로는 압착하지 않는다. */
    public static boolean fallOn(Level level, BlockPos pos, Entity falling, float distance) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (!active(entity)) return false;
        falling.causeFallDamage(distance, 1F, level.damageSources().fall());
        if (!(level instanceof ServerLevel) || !(falling instanceof Player player) || player.isSpectator()) return true;
        OilBatch batch = batch(entity);
        CompoundTag tag = entity.getPersistentData().getCompound(KEY);
        long now = level.getGameTime();
        if (batch.seeds() < OilBatch.SEEDS || batch.ready() || (tag.contains("lastStomp") && now - tag.getLong("lastStomp") < 4)) return true;
        tag.putLong("lastStomp", now);
        batch = batch.stomp();
        store(entity, batch, tag.getString("seed"));
        level.blockEvent(pos, entity.getBlockState().getBlock(), 1, 0);
        level.playSound(null, pos, SoundEvents.HONEY_BLOCK_STEP, SoundSource.BLOCKS, .8F, 1F);
        player.displayClientMessage(batch.ready() ? Component.translatable("message.coredefender.oil_ready")
                : Component.translatable("message.coredefender.oil_progress", batch.seeds(), OilBatch.SEEDS, batch.stomps(), OilBatch.STOMPS), true);
        return true;
    }

    public static void removed(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (level.isClientSide || !active(entity)) return;
        OilBatch batch = batch(entity);
        if (batch.stomps() == 0) {
            var id = ResourceLocation.tryParse(entity.getPersistentData().getCompound(KEY).getString("seed"));
            if (id != null) BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> Block.popResource(level, pos, new ItemStack(item, batch.seeds())));
        }
        store(entity, new OilBatch(0, 0), "");
    }
}
