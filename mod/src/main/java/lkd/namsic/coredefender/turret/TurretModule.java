package lkd.namsic.coredefender.turret;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class TurretModule extends Item {
    public static final String[] NAMES = {"damage", "speed", "pierce", "multishot", "ricochet", "aim", "range", "blast", "cluster", "flame_range", "burn"};
    public final int effect, grade;
    public final boolean legacy;

    public TurretModule(int effect, int grade, boolean legacy) {
        super(new Properties().stacksTo(16));
        this.effect = effect;
        this.grade = grade;
        this.legacy = legacy;
    }

    public boolean supports(int kind) {
        if (this.effect == 6 || this.effect == 9) {
            return kind == 1 || kind == 2 || kind == 3;
        }

        return this.effect < 2 || (kind == 0 && this.effect <= 5) || (kind == 1 && this.effect >= 6 && this.effect <= 8) || ((kind == 2 || kind == 3) && this.effect >= 9);
    }

    public MutableComponent description() {
        return switch (this.effect) {
            case 0 -> Component.translatable("module.coredefender.damage", 15 * this.grade);
            case 1 -> Component.translatable("module.coredefender.speed", 10 * this.grade);
            case 2 -> Component.translatable("module.coredefender.pierce", this.grade);
            case 3 -> Component.translatable("module.coredefender.multishot", this.grade);
            case 4 -> Component.translatable("module.coredefender.ricochet", this.grade);
            case 5 -> Component.translatable("module.coredefender.aim", 30 * this.grade);
            case 6, 9 -> Component.translatable("module.coredefender.range", 15 * this.grade, .5 * this.grade, 45 * this.grade);
            case 7 -> Component.translatable("module.coredefender.blast", 15 * this.grade);
            case 8 -> Component.translatable("module.coredefender.cluster", 2 * this.grade);
            default -> Component.translatable("module.coredefender.burn", 25 * this.grade);
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(this.description().withStyle(ChatFormatting.GRAY));
    }
}
