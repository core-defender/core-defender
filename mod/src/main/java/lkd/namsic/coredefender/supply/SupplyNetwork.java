package lkd.namsic.coredefender.supply;

import lkd.namsic.coredefender.Balance;
import lombok.Getter;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 서버 공용 보급 버퍼 (오버월드에 저장). 송신기가 넣고 수신기가 꺼낸다.
 */
public class SupplyNetwork extends SavedData {
    public static final String ID = "coredefender_supply";
    public static final Factory<SupplyNetwork> FACTORY = new Factory<>(SupplyNetwork::new, SupplyNetwork::load);

    @Getter
    private final ItemStackHandler buffer = new ItemStackHandler(Balance.SUPPLY_BUFFER_STACKS) {
        @Override
        protected void onContentsChanged(int slot) {
            SupplyNetwork.this.setDirty();
        }
    };

    public static SupplyNetwork get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, ID);
    }

    public int itemCount() {
        int count = 0;
        for (int i = 0; i < this.buffer.getSlots(); i++) {
            count += this.buffer.getStackInSlot(i).getCount();
        }

        return count;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("buffer", this.buffer.serializeNBT(registries));
        return tag;
    }

    static SupplyNetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        SupplyNetwork network = new SupplyNetwork();
        network.buffer.deserializeNBT(registries, tag.getCompound("buffer"));
        return network;
    }
}
