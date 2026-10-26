package lkd.namsic.coredefender.turret;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * Source retained on vanilla potion effects, including saved/hidden instances.
 */
public interface TurretEffect {
    record Source(BlockPos pos, UUID identity) {
    }

    @Nullable Source cd$source();

    void cd$source(@Nullable Source source);
}
