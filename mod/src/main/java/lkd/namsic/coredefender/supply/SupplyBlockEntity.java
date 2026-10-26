package lkd.namsic.coredefender.supply;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.registry.ModBlockEntities;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 보급 송신기(오버월드 등 디펜스 차원 밖) / 수신기(디펜스 차원) 공용 블록 엔티티.
 * 송신기는 자기 인벤토리 → 공용 버퍼, 수신기는 공용 버퍼 → 자기 인벤토리로 옮긴다.
 * 송신기는 단계에 따라 주변 청크를 로딩해 플레이어가 디펜스 차원에 있어도 생산 시설이 돈다.
 */
public class SupplyBlockEntity extends BlockEntity {
    /**
     * 송신기 청크 로딩 티켓 (블록이 남아 있는 한 유지)
     */
    public static final TicketController TICKETS = new TicketController(
        ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "supply"));

    @Getter
    private final boolean sender;
    @Getter
    private final ItemStackHandler inventory = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            SupplyBlockEntity.this.setChanged();
        }
    };
    @Getter
    private int tier;
    private int cooldown;
    private int forcedRadius = -1;

    public SupplyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPPLY.get(), pos, state);
        this.sender = state.getBlock() instanceof SupplyBlock block && block.isSender();
    }

    /**
     * 다른 차원에 있으면 동작하지 않는다 (송신기는 디펜스 차원 밖, 수신기는 디펜스 차원 안)
     */
    public boolean isOperational() {
        return this.level != null && this.sender != DefenseDimension.is(this.level);
    }

    public boolean upgrade() {
        if (this.tier >= Balance.SUPPLY_RATE.length - 1) {
            return false;
        }

        this.tier++;
        this.setChanged();
        if (this.level instanceof ServerLevel serverLevel) {
            this.updateChunkLoading(serverLevel);
        }

        return true;
    }

    // BlockEntityTicker requires position and state even though this ticker only needs the entity.
    @SuppressWarnings("unused")
    public static void serverTick(Level level, BlockPos pos, BlockState state, SupplyBlockEntity supply) {
        if (!(level instanceof ServerLevel serverLevel) || !supply.isOperational()) {
            return;
        }

        if (supply.forcedRadius < 0) {
            supply.updateChunkLoading(serverLevel);
        }

        if (--supply.cooldown > 0) {
            return;
        }

        supply.cooldown = Balance.SUPPLY_INTERVAL;
        IItemHandler buffer = SupplyNetwork.get(serverLevel.getServer()).getBuffer();
        if (supply.sender) {
            move(supply.inventory, buffer, Balance.SUPPLY_RATE[supply.tier]);
        } else {
            move(buffer, supply.inventory, Balance.SUPPLY_RATE[supply.tier]);
        }
    }

    private static void move(IItemHandler from, IItemHandler to, int budget) {
        for (int i = 0; i < from.getSlots() && budget > 0; i++) {
            ItemStack simulated = from.extractItem(i, budget, true);
            if (simulated.isEmpty()) {
                continue;
            }

            ItemStack remainder = ItemHandlerHelper.insertItemStacked(to, simulated, true);
            int movable = simulated.getCount() - remainder.getCount();
            if (movable <= 0) {
                continue;
            }

            ItemHandlerHelper.insertItemStacked(to, from.extractItem(i, movable, false), false);
            budget -= movable;
        }
    }


    private void updateChunkLoading(ServerLevel level) {
        if (!this.sender) {
            return;
        }

        this.setForced(level, false);
        this.forcedRadius = Balance.SUPPLY_CHUNK_RADIUS[this.tier];
        this.setForced(level, true);
    }

    private void setForced(ServerLevel level, boolean add) {
        if (this.forcedRadius < 0) {
            return;
        }

        int cx = this.worldPosition.getX() >> 4;
        int cz = this.worldPosition.getZ() >> 4;
        for (int dx = -this.forcedRadius; dx <= this.forcedRadius; dx++) {
            for (int dz = -this.forcedRadius; dz <= this.forcedRadius; dz++) {
                TICKETS.forceChunk(level, this.worldPosition, cx + dx, cz + dz, add, true);
            }
        }
    }

    /**
     * 블록이 제거될 때 호출
     */
    public void releaseChunks() {
        if (this.level instanceof ServerLevel serverLevel) {
            this.setForced(serverLevel, false);
            this.forcedRadius = -1;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", this.inventory.serializeNBT(registries));
        tag.putInt("tier", this.tier);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        this.tier = Math.clamp(tag.getInt("tier"), 0, Balance.SUPPLY_RATE.length - 1);
    }
}
