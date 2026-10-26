package lkd.namsic.coredefender.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface TurretArrowAccess {
    @Invoker("setPierceLevel")
    void cd$setPierceLevel(byte level);
}
