package lkd.namsic.coredefender.registry;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.turret.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

public final class ModTurrets {
    private ModTurrets() {}
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,CoreDefender.MOD_ID);
    public static final DeferredHolder<MenuType<?>,MenuType<TurretMenu>> MENU=MENUS.register("turret",()->IMenuTypeExtension.create(TurretMenu::new));
    public static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,CoreDefender.MOD_ID);
    public static final DeferredHolder<EntityType<?>,EntityType<StoneProjectile>> STONE_PROJECTILE=ENTITIES.register("stone_projectile",()->EntityType.Builder.of(StoneProjectile::new,MobCategory.MISC).sized(.25F,.25F).clientTrackingRange(8).updateInterval(1).build("coredefender:stone_projectile"));
}
