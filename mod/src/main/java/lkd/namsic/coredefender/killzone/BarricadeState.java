package lkd.namsic.coredefender.killzone;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 디펜스 차원의 바리케이드 HP (위치 → HP).
 */
public class BarricadeState extends SavedData {
    public static final String ID = "coredefender_barricades";
    public static final Factory<BarricadeState> FACTORY = new Factory<>(BarricadeState::new, BarricadeState::load);

    @Getter
    @Accessors(fluent = true)
    private final Long2IntOpenHashMap hp = new Long2IntOpenHashMap();

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        long[] positions = this.hp.keySet().toLongArray();
        int[] values = new int[positions.length];
        for (int i = 0; i < positions.length; i++) {
            values[i] = this.hp.get(positions[i]);
        }

        tag.putLongArray("positions", positions);
        tag.putIntArray("hp", values);
        return tag;
    }

    static BarricadeState load(CompoundTag tag, HolderLookup.Provider registries) {
        BarricadeState state = new BarricadeState();
        long[] positions = tag.getLongArray("positions");
        int[] values = tag.getIntArray("hp");
        for (int i = 0; i < Math.min(positions.length, values.length); i++) {
            state.hp.put(positions[i], values[i]);
        }

        return state;
    }
}
