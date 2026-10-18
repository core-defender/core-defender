package lkd.namsic.coredefender.wall;

import lkd.namsic.coredefender.supply.SupplyBlockEntity;
import lkd.namsic.coredefender.turret.BasicTurretBlockEntity;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 코어 창고 (디펜스 차원에 저장). 성벽 확장 등으로 시스템이 회수한 블록/아이템이 들어온다.
 * 가득 차면 코어 위에 떨어뜨린다.
 */
public class CoreStorage extends SavedData {
    public static final String ID = "coredefender_core_storage";
    public static final Factory<CoreStorage> FACTORY = new Factory<>(CoreStorage::new, CoreStorage::load);
    private static final int SIZE = 54;

    @Getter
    @Accessors(fluent = true)
    private final SimpleContainer container = new SimpleContainer(SIZE);

    public CoreStorage() {
        container.addListener(changed -> setDirty());
    }

    public static CoreStorage get(ServerLevel defense) {
        return defense.getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public static void open(ServerPlayer player) {
        ServerLevel level = WallManager.level(player.server);
        if (level == null) {
            return;
        }
        Container container = get(level).container;
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.sixRows(id, inventory, container),
                Component.translatable("container.coredefender.core_storage")));
    }

    /** 창고에 넣고, 남는 것은 코어 위에 떨어뜨린다. */
    public static void insert(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remainder = get(level).container.addItem(stack);
        if (!remainder.isEmpty()) {
            Block.popResource(level, WallManager.state(level).geometry().corePos().above(), remainder);
        }
    }

    /**
     * 블록을 제거하고 블록 자체(드롭)와 내부 인벤토리를 창고로 회수한다.
     */
    public static void salvage(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> items = new ArrayList<>(Block.getDrops(state, level, pos, blockEntity));
        items.addAll(takeContents(level, pos, blockEntity));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        items.forEach(item -> insert(level, item));
    }

    /** 블록 엔티티의 내용물을 꺼낸다 (꺼낸 뒤 제거하므로 제거 시 바닥에 쏟아지지 않는다) */
    private static List<ItemStack> takeContents(ServerLevel level, BlockPos pos, BlockEntity blockEntity) {
        List<ItemStack> items = new ArrayList<>();
        IItemHandler handler = switch (blockEntity) {
            case BasicTurretBlockEntity turret -> turret.getInventory();
            case SupplyBlockEntity supply -> supply.getInventory();
            case null -> null;
            default -> level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        };
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack taken = handler.extractItem(i, Integer.MAX_VALUE, false);
                if (!taken.isEmpty()) {
                    items.add(taken);
                }
            }
        } else if (blockEntity instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                items.add(container.removeItemNoUpdate(i));
            }
        }
        return items;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ContainerHelper.saveAllItems(tag, container.getItems(), registries);
        return tag;
    }

    static CoreStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        CoreStorage storage = new CoreStorage();
        ContainerHelper.loadAllItems(tag, storage.container.getItems(), registries);
        return storage;
    }
}
