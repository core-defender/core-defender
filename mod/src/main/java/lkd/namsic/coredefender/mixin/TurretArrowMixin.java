package lkd.namsic.coredefender.mixin;

import lkd.namsic.coredefender.turret.TurretCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Only tagged turret arrows use fixed damage and custom piercing/ricochet. Vanilla arrows are untouched. */
@Mixin(AbstractArrow.class)
public abstract class TurretArrowMixin {
    @Shadow protected abstract void doPostHurtEffects(LivingEntity target);
    @Inject(method="canHitEntity",at=@At("RETURN"),cancellable=true)
    private void cd$targets(Entity target,CallbackInfoReturnable<Boolean> ci) {
        AbstractArrow self=(AbstractArrow)(Object)this;
        if(TurretCombat.turretArrow(self))ci.setReturnValue(ci.getReturnValue() && TurretCombat.canArrowHit(self,target));
    }
    @Inject(method="onHitEntity",at=@At("HEAD"),cancellable=true)
    private void cd$hit(EntityHitResult hit,CallbackInfo ci) {
        AbstractArrow self=(AbstractArrow)(Object)this;
        if(!TurretCombat.turretArrow(self))return;
        if(hit.getEntity() instanceof LivingEntity victim) {
            Vec3 point=victim.getBoundingBox().inflate(.3).clip(self.position(),self.position().add(self.getDeltaMovement())).orElse(victim.getBoundingBox().getCenter());
            TurretCombat.arrowHit(self,victim,point,()->this.doPostHurtEffects(victim));
        }
        ci.cancel();
    }
    @Inject(method="onHitBlock",at=@At("HEAD"),cancellable=true)
    private void cd$block(BlockHitResult hit,CallbackInfo ci) {
        AbstractArrow self=(AbstractArrow)(Object)this;
        if(TurretCombat.turretArrow(self)) {self.discard();ci.cancel();}
    }
    @Inject(method="tick",at=@At("HEAD"))
    private void cd$tick(CallbackInfo ci) {
        AbstractArrow self=(AbstractArrow)(Object)this;
        if(TurretCombat.turretArrow(self)) {((TurretArrowAccess)self).cd$setPierceLevel((byte)1);if(self.tickCount>200)self.discard();}
    }
    @Inject(method="tick",at=@At("TAIL"))
    private void cd$restoreBounce(CallbackInfo ci) {
        AbstractArrow self=(AbstractArrow)(Object)this;
        var tag=self.getPersistentData();
        if(tag.getBoolean("cd_bouncePending")) {
            self.setDeltaMovement(tag.getDouble("cd_bounceX"),tag.getDouble("cd_bounceY"),tag.getDouble("cd_bounceZ"));
            tag.remove("cd_bouncePending");self.hasImpulse=true;
        }
    }
}
