package lkd.namsic.coredefender;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class DefenseDimension {
    /** 서버 공유 디펜스 차원 (data/coredefender/dimension/defense.json) */
    public static final ResourceKey<Level> KEY =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "defense"));

    private DefenseDimension() {
    }

    public static boolean is(Level level) {
        return level.dimension() == KEY;
    }
}
