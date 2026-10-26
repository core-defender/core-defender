package lkd.namsic.coredefender.mixin;

import org.jspecify.annotations.Nullable;

import lkd.namsic.coredefender.turret.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/**
 * Keep vanilla potion timing/stacking while scoping turret-derived effect damage.
 */
@Mixin(MobEffectInstance.class)
public abstract class TurretEffectMixin implements TurretEffect {
    @Unique
    private @Nullable Source cd$source;

    public @Nullable Source cd$source() {
        return this.cd$source;
    }

    public void cd$source(@Nullable Source source) {
        this.cd$source = source;
    }

    @Inject(method = "<init>(Lnet/minecraft/world/effect/MobEffectInstance;)V", at = @At("RETURN"))
    private void cd$copy(MobEffectInstance other, CallbackInfo ci) {
        this.cd$source = ((TurretEffect) other).cd$source();
    }

    @Inject(method = "setDetailsFrom", at = @At("RETURN"))
    private void cd$details(MobEffectInstance other, CallbackInfo ci) {
        this.cd$source = ((TurretEffect) other).cd$source();
    }

    @Inject(method = "update", at = @At("HEAD"))
    private void cd$update(MobEffectInstance other, CallbackInfoReturnable<Boolean> ci) {
        MobEffectInstance self = (MobEffectInstance) (Object) this;
        if (other.getAmplifier() > self.getAmplifier() || other.getAmplifier() == self.getAmplifier() && (other.isInfiniteDuration() || other.getDuration() >= self.getDuration())) {
            this.cd$source = ((TurretEffect) other).cd$source();
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/effect/MobEffect;applyEffectTick(Lnet/minecraft/world/entity/LivingEntity;I)Z"))
    private boolean cd$effectTick(MobEffect effect, LivingEntity entity, int amplifier) {
        return TurretCombat.effectTick(effect, entity, amplifier, this.cd$source);
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void cd$save(CallbackInfoReturnable<Tag> ci) {
        if (this.cd$source != null && ci.getReturnValue() instanceof CompoundTag tag) {
            tag.putLong("cd_origin", this.cd$source.pos().asLong());
            tag.putUUID("cd_turret", this.cd$source.identity());
        }
    }

    @Inject(method = "load", at = @At("RETURN"))
    private static void cd$load(CompoundTag tag, CallbackInfoReturnable<@Nullable MobEffectInstance> ci) {
        if (ci.getReturnValue() != null && tag.hasUUID("cd_turret")) {
            ((TurretEffect) ci.getReturnValue()).cd$source(new Source(BlockPos.of(tag.getLong("cd_origin")), tag.getUUID("cd_turret")));
        }
    }
}
