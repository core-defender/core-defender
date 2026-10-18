package lkd.namsic.coredefender.wall;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.Map;

/** 디펜스 차원에 저장되는 성벽 상태. */
public class WallState extends SavedData {
    public static final String ID = "coredefender_wall";
    public static final Factory<WallState> FACTORY = new Factory<>(WallState::new, WallState::load);

    @Getter
    @Accessors(fluent = true)
    boolean built;
    int centerX;
    int centerZ;
    int baseY;
    int half = 1;
    int height = 5;
    int thickness = 2;
    /** 변(N/E/S/W)별 강화 단계. 확장해도 유지된다. */
    final int[] sideLevels = new int[WallGeometry.SIDES.length];
    @Getter
    @Accessors(fluent = true)
    final Map<String, WallSegment> segments = new LinkedHashMap<>();


    public WallGeometry geometry() {
        return new WallGeometry(centerX, centerZ, baseY, half, height, thickness);
    }


    public int sideLevel(String side) {
        return sideLevels[sideIndex(side)];
    }

    static int sideIndex(String side) {
        for (int i = 0; i < WallGeometry.SIDES.length; i++) {
            if (WallGeometry.SIDES[i].equals(side)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown wall side: " + side);
    }

    /** 현재 크기에 맞춰 구간을 새로 만든다 (기존 구간 HP 는 버리고 변별 강화 단계는 유지) */
    void resetSegments() {
        segments.clear();
        for (String id : geometry().segmentIds()) {
            segments.put(id, new WallSegment(WallManager.segmentMaxHp(sideLevel(id.substring(0, 1)))));
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("built", built);
        tag.putInt("centerX", centerX);
        tag.putInt("centerZ", centerZ);
        tag.putInt("baseY", baseY);
        tag.putInt("half", half);
        tag.putInt("height", height);
        tag.putInt("thickness", thickness);
        tag.putIntArray("sideLevels", sideLevels);
        CompoundTag segmentsTag = new CompoundTag();
        segments.forEach((id, segment) -> segmentsTag.put(id, segment.save()));
        tag.put("segments", segmentsTag);
        return tag;
    }

    static WallState load(CompoundTag tag, HolderLookup.Provider registries) {
        WallState state = new WallState();
        state.built = tag.getBoolean("built");
        state.centerX = tag.getInt("centerX");
        state.centerZ = tag.getInt("centerZ");
        state.baseY = tag.getInt("baseY");
        state.half = tag.getInt("half");
        state.height = tag.getInt("height");
        state.thickness = tag.getInt("thickness");
        int[] levels = tag.getIntArray("sideLevels");
        System.arraycopy(levels, 0, state.sideLevels, 0, Math.min(levels.length, state.sideLevels.length));
        CompoundTag segmentsTag = tag.getCompound("segments");
        for (String id : segmentsTag.getAllKeys()) {
            state.segments.put(id, WallSegment.load(segmentsTag.getCompound(id)));
        }
        return state;
    }
}
