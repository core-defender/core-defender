package lkd.namsic.coredefender.compat.jei;

import java.util.List;

import lkd.namsic.coredefender.CoreDefender;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI 가 어노테이션 스캔으로 찾는 진입점이다. JEI 가 없으면 로드되지 않는다.
 */
@JeiPlugin
@SuppressWarnings("unused")
public final class CoreDefenderJeiPlugin implements IModPlugin {
    private static final ResourceLocation POT = ResourceLocation.parse("vinery:grapevine_pot");

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new SeedOilPressingCategory(guiHelper), new TurretInfoCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(SeedOilPressingCategory.TYPE, List.of(SeedOilPressingCategory.Recipe.INSTANCE));
        registration.addRecipes(TurretInfoCategory.TYPE, TurretInfoCategory.turrets());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        BuiltInRegistries.ITEM.getOptional(POT).ifPresent(pot -> registration.addRecipeCatalysts(SeedOilPressingCategory.TYPE, pot));
    }
}
