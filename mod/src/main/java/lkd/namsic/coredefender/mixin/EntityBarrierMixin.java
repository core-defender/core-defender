package lkd.namsic.coredefender.mixin;

import lkd.namsic.coredefender.wall.BarrierSystem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
// Registered entry point: invoked by the game registry, scripts, or annotation discovery.
@SuppressWarnings("unused")
abstract class EntityBarrierMixin {
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void coredefender$barrier(Vec3 requested, CallbackInfoReturnable<Vec3> callback) {
        callback.setReturnValue(BarrierSystem.clipMovement((Entity) (Object) this, callback.getReturnValue()));
    }
}
