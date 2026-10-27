package lkd.namsic.coredefender.compat.jei;

import java.util.Arrays;
import java.util.List;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.compat.OilBatch;
import lkd.namsic.coredefender.compat.VineryOil;
import lkd.namsic.coredefender.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 포도 덩굴 화분에 같은 씨앗을 채우고 밟아 종자유 병을 얻는 수동 압착.
 */
public final class SeedOilPressingCategory implements IRecipeCategory<SeedOilPressingCategory.Recipe> {
    public static final RecipeType<Recipe> TYPE = RecipeType.create(CoreDefender.MOD_ID, "seed_oil_pressing", Recipe.class);
    private static final int WIDTH = 124;
    private static final int HEIGHT = 46;
    private final IDrawable icon;

    public SeedOilPressingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.SEED_OIL_BOTTLE.get());
    }

    @Override
    public RecipeType<Recipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.coredefender.seed_oil_pressing");
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
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        List<ItemStack> seeds = Arrays.stream(Ingredient.of(VineryOil.SEEDS).getItems())
            .map(stack -> stack.copyWithCount(OilBatch.SEEDS))
            .toList();
        builder.addInputSlot(1, 1).setStandardSlotBackground().addItemStacks(seeds);
        builder.addInputSlot(1, 21).setStandardSlotBackground().addItemStack(Items.GLASS_BOTTLE.getDefaultInstance());
        builder.addOutputSlot(99, 11).setOutputSlotBackground().addItemStack(ModItems.SEED_OIL_BOTTLE.get().getDefaultInstance());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrowWidget().setPosition(64, 11);
        builder.addText(Component.translatable("jei.coredefender.seed_oil_stomps", OilBatch.STOMPS), 36, 9)
            .setPosition(26, 15)
            .setColor(0xFF808080);
    }

    public enum Recipe {
        INSTANCE
    }
}
