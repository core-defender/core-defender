package lkd.namsic.coredefender.wall;

import lombok.experimental.UtilityClass;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 성벽/바리케이드가 받은 피해를 맞은 위치에 떠오르는 숫자로 보여준다 (바닐라 텍스트 디스플레이 엔티티).
 * 저장되지 않도록 월드 로드 시 남은 숫자는 제거한다 (WaveEvents#onEntityJoin).
 */
@UtilityClass
public class DamageNumbers {
    public static final String TAG = "cd_damage_number";
    private static final int LIFETIME = 20;
    private static final double RISE_PER_TICK = 0.04;

    private record Floating(Display.TextDisplay display, int[] age) {
    }

    private static final List<Floating> ACTIVE = new ArrayList<>();

    public static void spawn(ServerLevel level, Vec3 pos, int amount, ChatFormatting color) {
        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        if (display == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("text", Component.Serializer.toJson(
                Component.literal("-" + amount).withStyle(color, ChatFormatting.BOLD), level.registryAccess()));
        tag.putString("billboard", "center");
        tag.putString("alignment", "center");
        tag.putInt("background", 0);
        tag.putBoolean("shadow", true);
        // 맞은 블록 안쪽에서 생성되므로 블록을 투과해서 보이게 한다
        tag.putBoolean("see_through", true);
        tag.putInt("teleport_duration", 2);
        display.load(tag);
        // 같은 블록을 여러 번 맞아도 숫자가 겹치지 않게 살짝 흩뿌린다
        double jitter = 0.3;
        display.moveTo(pos.x + (level.getRandom().nextDouble() - 0.5) * jitter,
                pos.y + 0.3, pos.z + (level.getRandom().nextDouble() - 0.5) * jitter);
        display.addTag(TAG);
        level.addFreshEntity(display);
        ACTIVE.add(new Floating(display, new int[]{0}));
    }

    public static void tick() {
        Iterator<Floating> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Floating floating = it.next();
            Display.TextDisplay display = floating.display();
            if (++floating.age()[0] > LIFETIME || display.isRemoved()) {
                display.discard();
                it.remove();
                continue;
            }
            display.moveTo(display.getX(), display.getY() + RISE_PER_TICK, display.getZ());
        }
    }

    public static boolean isDamageNumber(Entity entity) {
        return entity.getTags().contains(TAG);
    }
}
