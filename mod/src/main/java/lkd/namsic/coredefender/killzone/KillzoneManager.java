package lkd.namsic.coredefender.killzone;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.wall.DamageNumbers;
import lkd.namsic.coredefender.wave.FlowFieldManager;
import lombok.experimental.UtilityClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** 킬존 장애물(바리케이드) HP 관리. */
@UtilityClass
public class KillzoneManager {
    public static BarricadeState state(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BarricadeState.FACTORY, BarricadeState.ID);
    }

    public static void register(ServerLevel level, BlockPos pos) {
        if (!DefenseDimension.is(level)) {
            return;
        }
        BarricadeState state = state(level);
        state.hp().put(pos.asLong(), Balance.BARRICADE_HP);
        state.setDirty();
        FlowFieldManager.invalidate();
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        if (!DefenseDimension.is(level)) {
            return;
        }
        BarricadeState state = state(level);
        if (state.hp().containsKey(pos.asLong())) {
            state.hp().remove(pos.asLong());
            state.setDirty();
        }
        level.destroyBlockProgress(crackId(pos), pos, -1);
        FlowFieldManager.invalidate();
    }

    public static boolean isBarricade(ServerLevel level, BlockPos pos) {
        return state(level).hp().containsKey(pos.asLong());
    }

    /** 피해를 주고, HP 가 0 이 되면 블록을 부순다. 바리케이드가 아니면 false. */
    public static boolean damage(ServerLevel level, BlockPos pos, int amount) {
        BarricadeState state = state(level);
        long key = pos.asLong();
        if (!state.hp().containsKey(key)) {
            return false;
        }
        DamageNumbers.spawn(level, Vec3.atCenterOf(pos), amount, ChatFormatting.YELLOW);
        int hp = state.hp().get(key) - amount;
        if (hp <= 0) {
            level.destroyBlock(pos, false);
            return true;
        }
        state.hp().put(key, hp);
        state.setDirty();
        int stage = Math.clamp((int) ((1.0 - (double) hp / Balance.BARRICADE_HP) * 10), 0, 9);
        level.destroyBlockProgress(crackId(pos), pos, stage);
        return true;
    }

    private static int crackId(BlockPos pos) {
        // 성벽 균열 ID(-1 ~ -0x40000000)와 겹치지 않는 범위
        return -0x40000001 - (Long.hashCode(pos.asLong()) & 0x3FFFFFFF);
    }
}
