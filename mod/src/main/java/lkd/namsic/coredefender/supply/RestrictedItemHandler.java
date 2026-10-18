package lkd.namsic.coredefender.supply;

import lombok.RequiredArgsConstructor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** 외부(호퍼/파이프)에 넣기 전용 또는 빼기 전용으로 노출하는 래퍼. */
@RequiredArgsConstructor
public class RestrictedItemHandler implements IItemHandler {
    private final IItemHandler delegate;
    private final boolean allowInsert;
    private final boolean allowExtract;

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return allowInsert ? delegate.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return allowExtract ? delegate.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return allowInsert && delegate.isItemValid(slot, stack);
    }
}
