package lkd.namsic.coredefender.wall;

import net.minecraft.nbt.CompoundTag;

/** 성벽 구간 하나 (한 변을 청크 단위로 나눈 조각). */
public final class WallSegment {
    int hp;
    int maxHp;
    boolean collapsed;

    WallSegment(int maxHp) {
        this.hp = maxHp;
        this.maxHp = maxHp;
    }

    public int hp() {
        return hp;
    }

    public int maxHp() {
        return maxHp;
    }

    public boolean collapsed() {
        return collapsed;
    }

    /** 균열 단계 0~9 (0 = 균열 없음) */
    int crackStage() {
        if (collapsed || hp >= maxHp) {
            return 0;
        }
        return Math.clamp((int) ((1.0 - (double) hp / maxHp) * 10), 0, 9);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("hp", hp);
        tag.putInt("maxHp", maxHp);
        tag.putBoolean("collapsed", collapsed);
        return tag;
    }

    static WallSegment load(CompoundTag tag) {
        WallSegment segment = new WallSegment(tag.getInt("maxHp"));
        segment.hp = tag.getInt("hp");
        segment.collapsed = tag.getBoolean("collapsed");
        return segment;
    }
}
