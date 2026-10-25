package lkd.namsic.coredefender.turret;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

public class TurretModule extends Item {
    public static final String[] NAMES = {"damage", "speed", "pierce", "multishot", "ricochet", "aim", "stone_range", "blast", "cluster", "flame_range", "burn"};
    public final int effect, grade;
    public TurretModule(int effect, int grade) { super(new Properties().stacksTo(16)); this.effect = effect; this.grade = grade; }
    public boolean supports(int kind) { return this.effect < 2 || (kind == 0 && this.effect <= 5) || (kind == 1 && this.effect >= 6 && this.effect <= 8) || (kind == 2 && this.effect >= 9); }
    public String description() {
        return switch(this.effect) {
            case 0 -> "공격력 +" + 15 * this.grade + "%";
            case 1 -> "공격속도 +" + 10 * this.grade + "% (화염: 휴지기만 감소)";
            case 2 -> "추가 관통 " + this.grade + "회 · 다음 피해 ×0.8";
            case 3 -> "동시에 다른 적 +" + this.grade + "명 · 실제 발사 수만큼 소비";
            case 4 -> "마지막 관통 명중 후 도탄 " + this.grade + "회 · 피해 ×0.75";
            case 5 -> "조준속도 +" + 30 * this.grade + "%";
            case 6 -> "사거리 +" + 15 * this.grade + "%";
            case 7 -> "주 폭발/파편 폭발 반경 +" + 15 * this.grade + "%";
            case 8 -> "착탄 후 작은 폭발 파편 " + 2 * this.grade + "개";
            case 9 -> "사거리 +" + .5 * this.grade + "m · 방사각 +" + 45 * this.grade + "°";
            default -> "잔류 화상 피해 +" + 25 * this.grade + "%";
        };
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.literal(this.description()));
        lines.add(Component.literal("동일 모듈 중복 가능 · 두 슬롯 효과 합산"));
    }
}
