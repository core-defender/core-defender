package lkd.namsic.coredefender.mixin;

import lkd.namsic.coredefender.turret.TurretCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class EnemyDamageMixin {
    // Reset before and after hurt so independent attacks in the same tick all apply.
    @Inject(method = "hurt", at = {@At("HEAD"), @At("RETURN")})
    private void cd$removeEnemyHurtImmunity(DamageSource source, float amount, CallbackInfoReturnable<Boolean> callback) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (TurretCombat.isTarget(entity)) {
            entity.invulnerableTime = 0;
        }
    }
}
