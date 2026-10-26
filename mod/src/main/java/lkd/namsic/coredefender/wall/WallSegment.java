package lkd.namsic.coredefender.wall;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.minecraft.nbt.CompoundTag;

/**
 * 성벽 구간 하나 (한 변을 청크 단위로 나눈 조각).
 */
@Getter
@Accessors(fluent = true)
public final class WallSegment {
    int hp;
    int maxHp;
    boolean collapsed;

    WallSegment(int maxHp) {
        this.hp = maxHp;
        this.maxHp = maxHp;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("hp", this.hp);
        tag.putInt("maxHp", this.maxHp);
        tag.putBoolean("collapsed", this.collapsed);
        return tag;
    }

    static WallSegment load(CompoundTag tag) {
        WallSegment segment = new WallSegment(tag.getInt("maxHp"));
        segment.hp = tag.getInt("hp");
        segment.collapsed = tag.getBoolean("collapsed");
        return segment;
    }
}
