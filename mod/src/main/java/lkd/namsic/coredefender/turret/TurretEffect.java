package lkd.namsic.coredefender.turret;

import net.minecraft.core.BlockPos;
import java.util.UUID;

/** Source retained on vanilla potion effects, including saved/hidden instances. */
public interface TurretEffect {
    record Source(BlockPos pos, UUID identity) {}
    Source cd$source();
    void cd$source(Source source);
}
