package lkd.namsic.coredefender.compat.jei;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.registry.ModBlocks;
import lkd.namsic.coredefender.registry.ModItems;
import lkd.namsic.coredefender.turret.BasicTurretBlockEntity;
import lkd.namsic.coredefender.turret.TurretModule;
import lkd.namsic.coredefender.turret.TurretStats;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 포탑별 기본 성능과 넣을 수 있는 자원·모듈. 수치는 모듈 없는 TurretStats 에서 계산해 코드와 어긋나지 않는다.
 */
public final class TurretInfoCategory implements IRecipeCategory<TurretInfoCategory.Turret> {
    public static final RecipeType<Turret> TYPE = RecipeType.create(CoreDefender.MOD_ID, "turret_info", Turret.class);
    private static final int WIDTH = 170;
    private static final int HEIGHT = 124;
    private static final int ROWS_TOP = 44;
    private static final int ROW_HEIGHT = 20;
    private static final int SLOTS_LEFT = 30;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int LABEL_COLOR = 0xFF808080;
    private static final DecimalFormat NUMBER = new DecimalFormat("0.##");
    private static final ResourceLocation SEED_OIL_BUCKET = ResourceLocation.parse("createaddition:seed_oil_bucket");
    private final IDrawable icon;

    public TurretInfoCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModBlocks.BASIC_TURRET_ITEM.get());
    }

    public static List<Turret> turrets() {
        return List.of(
            new Turret(TurretStats.ARROW, ModBlocks.BASIC_TURRET_ITEM.get()),
            new Turret(TurretStats.STONE, ModBlocks.STONE_TURRET_ITEM.get()),
            new Turret(TurretStats.OIL, ModBlocks.OIL_TURRET_ITEM.get())
        );
    }

    @Override
    public RecipeType<Turret> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.coredefender.turret_info");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Turret turret, IFocusGroup focuses) {
        TurretStats base = base(turret.kind());
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStack(turret.item().getDefaultInstance());
        int row = 0;
        if (turret.kind() == TurretStats.ARROW) {
            List<ItemStack> arrows = List.of(Items.ARROW.getDefaultInstance(),
                PotionContents.createItemStack(Items.TIPPED_ARROW, Potions.POISON), Items.SPECTRAL_ARROW.getDefaultInstance());
            for (int i = 0; i < arrows.size(); i++) {
                this.slot(builder, row, i).addItemStack(arrows.get(i));
            }

            this.slot(builder, row, arrows.size()).addItemStack(ModItems.CRUDE_ARROW.get().getDefaultInstance())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.coredefender.turret.damage", number(base.crudeArrowDamage()))));
            row++;
        } else if (turret.kind() == TurretStats.STONE) {
            this.slot(builder, row, 0).addIngredients(Ingredient.of(BasicTurretBlockEntity.STONE));
            this.slot(builder, row, 1).addIngredients(Ingredient.of(BasicTurretBlockEntity.DEEP))
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.coredefender.turret.deep")));
            row++;
            List<ItemStack> fuels = List.of(Items.COAL.getDefaultInstance(), Items.CHARCOAL.getDefaultInstance(), Items.COAL_BLOCK.getDefaultInstance());
            for (int i = 0; i < fuels.size(); i++) {
                int shots = BasicTurretBlockEntity.fuelValue(fuels.get(i));
                this.slot(builder, row, i).addItemStack(fuels.get(i))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.coredefender.turret.shots", shots)));
            }

            row++;
            double speed = (double) base.interval(0) / base.interval(1) - 1;
            this.slot(builder, row, 0).addItemStack(Items.BLAZE_POWDER.getDefaultInstance())
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.coredefender.turret.amp_speed", percent(speed)));
                    tooltip.add(Component.translatable("jei.coredefender.turret.amp_shots", TurretStats.AMP_SHOTS));
                });
            this.slot(builder, row, 1).addItemStack(Items.GUNPOWDER.getDefaultInstance())
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.coredefender.turret.amp_power",
                        percent(base.damage(false, 2) / base.damage(false, 0) - 1),
                        percent(base.range(false, 2) / base.range(false, 0) - 1),
                        percent(base.projectileSpeed(2) / base.projectileSpeed(0) - 1)));
                    tooltip.add(Component.translatable("jei.coredefender.turret.amp_shots", TurretStats.AMP_SHOTS));
                });
            row++;
        } else {
            IRecipeSlotBuilder oil = this.slot(builder, row, 0).addItemStack(ModItems.SEED_OIL_BOTTLE.get().getDefaultInstance());
            BuiltInRegistries.ITEM.getOptional(SEED_OIL_BUCKET).ifPresent(bucket -> oil.addItemStack(bucket.getDefaultInstance()));
            row++;
            this.slot(builder, row, 0).addItemStack(Items.LAVA_BUCKET.getDefaultInstance())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.coredefender.turret.amp_lava",
                    TurretStats.OIL_LAVA_PER_POUR, percent(base.damage(false, 3) / base.damage(false, 0) - 1))));
            row++;
        }

        List<List<ItemStack>> modules = modules(turret.kind());
        for (int i = 0; i < modules.size(); i++) {
            this.slot(builder, row, i).addItemStacks(modules.get(i));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Turret turret, IFocusGroup focuses) {
        builder.addText(stats(turret.kind()), WIDTH - 22, ROWS_TOP - 4).setPosition(22, 0).setColor(TEXT_COLOR);
        List<String> labels = switch (turret.kind()) {
            case TurretStats.ARROW -> List.of("ammo", "modules");
            case TurretStats.STONE -> List.of("ammo", "fuel", "amp", "modules");
            default -> List.of("ammo", "amp", "modules");
        };
        for (int i = 0; i < labels.size(); i++) {
            builder.addText(Component.translatable("jei.coredefender.turret." + labels.get(i)), SLOTS_LEFT - 2, 10)
                .setPosition(0, ROWS_TOP + i * ROW_HEIGHT + 5)
                .setColor(LABEL_COLOR);
        }
    }

    private IRecipeSlotBuilder slot(IRecipeLayoutBuilder builder, int row, int column) {
        return builder.addSlot(RecipeIngredientRole.INPUT, SLOTS_LEFT + column * 18 + 1, ROWS_TOP + row * ROW_HEIGHT + 1).setStandardSlotBackground();
    }

    private static List<FormattedText> stats(int kind) {
        TurretStats s = base(kind);
        String seconds = number(s.interval(0) / 20.0);
        return switch (kind) {
            case TurretStats.ARROW -> List.of(
                Component.translatable("jei.coredefender.turret.arrow_damage", number(s.damage(false, 0)), number(s.crudeArrowDamage())),
                Component.translatable("jei.coredefender.turret.cycle", seconds, number(s.range(false, 0))));
            case TurretStats.STONE -> List.of(
                Component.translatable("jei.coredefender.turret.stone_damage", number(s.damage(false, 0)), number(s.radius(false))),
                Component.translatable("jei.coredefender.turret.stone_deep", number(s.damage(true, 0)), number(s.radius(true))),
                Component.translatable("jei.coredefender.turret.stone_range", number(s.range(false, 0)), number(s.range(true, 0))),
                Component.translatable("jei.coredefender.turret.every", seconds));
            default -> List.of(
                Component.translatable("jei.coredefender.turret.oil_damage", number(s.damage(false, 0)), TurretStats.FIRE_HITS),
                Component.translatable("jei.coredefender.turret.oil_pour", seconds, TurretStats.OIL_PER_POUR),
                Component.translatable("jei.coredefender.turret.oil_area", number(s.range(false, 0)), number(s.detectionRange()), number(s.angle())));
        };
    }

    /**
     * 장착 가능한 모듈 종류마다 I~III 를 한 칸에서 돌려 보여 준다. 구형 사거리 모듈은 교환 전용이라 뺀다.
     */
    private static List<List<ItemStack>> modules(int kind) {
        List<List<ItemStack>> result = new ArrayList<>();
        for (String name : TurretModule.NAMES) {
            List<ItemStack> grades = new ArrayList<>();
            for (int grade = 1; grade <= 3; grade++) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "module_" + name + "_" + grade));
                if (item instanceof TurretModule module && !module.legacy && module.supports(kind)) {
                    grades.add(item.getDefaultInstance());
                }
            }

            if (!grades.isEmpty()) {
                result.add(grades);
            }
        }

        return result;
    }

    private static TurretStats base(int kind) {
        return new TurretStats(kind, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static String number(double value) {
        return NUMBER.format(value);
    }

    private static String percent(double ratio) {
        return NUMBER.format(Math.round(ratio * 100));
    }

    public record Turret(int kind, Item item) {
    }
}
