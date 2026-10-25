package lkd.namsic.coredefender.turret;

import java.util.List;
import lkd.namsic.coredefender.mixin.TurretArrowAccess;
import lkd.namsic.coredefender.mixin.TurretDamageAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.wave.WaveMobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.UUID;

@EventBusSubscriber(modid=CoreDefender.MOD_ID)
public final class TurretCombat {
    private TurretCombat() {}
    private static final ThreadLocal<Integer> DAMAGE_SCOPE=ThreadLocal.withInitial(()->0);
    public static boolean isTarget(Entity entity) {
        return entity instanceof Mob && entity.isAlive() && (WaveMobs.isWaveMob(entity) || BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals("dummmmmmy:target_dummy"));
    }
    @SubscribeEvent public static void noKnockback(LivingKnockBackEvent event) { if(DAMAGE_SCOPE.get()>0)event.setCanceled(true); }
    @SubscribeEvent public static void potionSource(MobEffectEvent.Added event) {
        if(event.getEffectSource() instanceof AbstractArrow arrow && turretArrow(arrow)) {
            CompoundTag tag=arrow.getPersistentData();
            if(tag.hasUUID("cd_turret"))((TurretEffect)event.getEffectInstance()).cd$source(new TurretEffect.Source(BlockPos.of(tag.getLong("cd_origin")),tag.getUUID("cd_turret")));
        }
    }
    public static boolean effectTick(MobEffect effect,LivingEntity victim,int amplifier,TurretEffect.Source source) {
        // Minecraft owns this level; borrowers must not close it.
        //noinspection resource
        if(source==null || !(victim.level() instanceof ServerLevel level))return effect.applyEffectTick(victim,amplifier);
        Vec3 velocity=victim.getDeltaMovement();float health=victim.getHealth();DAMAGE_SCOPE.set(DAMAGE_SCOPE.get()+1);
        try {return effect.applyEffectTick(victim,amplifier);}
        finally {
            victim.setDeltaMovement(velocity);DAMAGE_SCOPE.set(DAMAGE_SCOPE.get()-1);
            if(level.isLoaded(source.pos())&&level.getBlockEntity(source.pos()) instanceof BasicTurretBlockEntity turret&&turret.identity.equals(source.identity()))turret.recordDamage(Math.max(0,health-victim.getHealth()));
        }
    }
    /** The scope includes potion effects. Preserve pre-hit velocity and invulnerability state, not just horizontal knockback. */
    public static boolean damage(ServerLevel level,LivingEntity victim,DamageSource source,double amount,BlockPos origin,UUID identity,Runnable effects) {
        Vec3 velocity=victim.getDeltaMovement();int immunity=victim.invulnerableTime;float health=victim.getHealth();
        var access=(TurretDamageAccess)victim;float lastHurt=access.cd$lastHurt();
        DAMAGE_SCOPE.set(DAMAGE_SCOPE.get()+1);
        try {
            victim.invulnerableTime=0;
            boolean hit=victim.hurt(source,(float)amount);
            if(hit && effects!=null)effects.run();
            return hit;
        } finally {
            victim.setDeltaMovement(velocity);victim.invulnerableTime=immunity;access.cd$lastHurt(lastHurt);DAMAGE_SCOPE.set(DAMAGE_SCOPE.get()-1);
            if(level.isLoaded(origin) && level.getBlockEntity(origin) instanceof BasicTurretBlockEntity turret && turret.identity.equals(identity))turret.recordDamage(Math.max(0,health-victim.getHealth()));
        }
    }
    public static DamageSource fireSource(ServerLevel level) { return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.parse("coredefender:turret_fire")))); }
    public static boolean clear(ServerLevel level,Vec3 from,Vec3 to) { return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty())).getType()==HitResult.Type.MISS; }
    public record Trajectory(Vec3 velocity,double ticks) {}
    /** Discrete drag/gravity equation, matching arrow and thrown-stone integration. Low arc with movement prediction. */
    public static Trajectory trajectory(Vec3 origin,Vec3 target,Vec3 movement,double speed,double gravity) {
        double previous=Double.POSITIVE_INFINITY;
        for(double t=.25;t<=100;t+=.25) {
            double factor=(1-Math.pow(.99,t))/.01;
            Vec3 predicted=target.add(movement.scale(t));
            Vec3 velocity=predicted.subtract(origin).add(0,gravity*(t-factor)/.01,0).scale(1/factor);
            double required=velocity.length();
            if(required<=speed && previous>speed)return new Trajectory(velocity.normalize().scale(speed),t);
            previous=required;
        }
        return null;
    }
    public static Vec3 aim(Vec3 from,Mob target,double speed,double gravity) {
        var shot=trajectory(from,target.getBoundingBox().getCenter(),prediction(target),speed,gravity);
        return shot==null?null:shot.velocity().normalize();
    }
    public static boolean clearArc(ServerLevel level,Vec3 from,Mob target,double speed) {
        var shot=trajectory(from,target.getBoundingBox().getCenter(),prediction(target),speed,.05);
        if(shot==null)return false;
        Vec3 point=from,velocity=shot.velocity();
        for(int i=0;i<Math.ceil(shot.ticks());i++) {
            Vec3 next=point.add(velocity.scale(Math.min(1,shot.ticks()-i)));
            if(!clear(level,point,next))return false;
            point=next;velocity=velocity.scale(.99).add(0,-.05,0);
        }
        return true;
    }
    private static Vec3 prediction(Mob target) { Vec3 movement=target.getDeltaMovement();return target.onGround()?new Vec3(movement.x,0,movement.z):movement; }
    public static boolean inCone(Vec3 from,Vec3 to,float yaw,double range,double degrees) {
        Vec3 delta=to.subtract(from);
        if(Math.abs(delta.y)>2 || delta.horizontalDistance()>range)return false;
        double target=Math.toDegrees(Math.atan2(-delta.x,delta.z));
        return degrees>=360 || Math.abs(Mth.wrapDegrees(target-yaw))<=degrees/2;
    }
    public static boolean fireImmune(LivingEntity e) { return e.fireImmune() || e.hasEffect(MobEffects.FIRE_RESISTANCE); }
    public static void flameParticles(ServerLevel level,Vec3 origin,float yaw,double range,double angle) {
        for(int i=0;i<6;i++) {
            double a=Math.toRadians(yaw+(level.random.nextDouble()-.5)*angle),r=level.random.nextDouble()*range;
            Vec3 end=origin.add(-Math.sin(a)*r,(level.random.nextDouble()-.5)*.7,Math.cos(a)*r);
            if(clear(level,origin,end))level.sendParticles(ParticleTypes.FLAME,end.x,end.y,end.z,1,.04,.04,.04,0);
        }
    }
    public static void burn(Mob mob,BasicTurretBlockEntity turret,double damage) {
        CompoundTag burns=mob.getPersistentData().getCompound("cd_burns");
        String key=turret.identity.toString();CompoundTag burn=burns.getCompound(key);
        // Minecraft owns this level; borrowers must not close it.
        //noinspection resource
        long now=mob.level().getGameTime();
        if(!burn.contains("next") || burn.getLong("expires")<now)burn.putLong("next",now+20);
        burn.putLong("expires",now+80);burn.putDouble("damage",damage);burn.putLong("origin",turret.getBlockPos().asLong());burn.putUUID("identity",turret.identity);
        burns.put(key,burn);mob.getPersistentData().put("cd_burns",burns);
    }
    @SubscribeEvent public static void tickBurn(EntityTickEvent.Post event) {
        // Minecraft owns this level; borrowers must not close it.
        //noinspection resource
        if(!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level))return;
        CompoundTag root=mob.getPersistentData();if(!root.contains("cd_burns"))return;
        CompoundTag burns=root.getCompound("cd_burns");long now=level.getGameTime();
        for(String key:List.copyOf(burns.getAllKeys())) {
            CompoundTag b=burns.getCompound(key);
            if(now>b.getLong("expires") || !isTarget(mob) || fireImmune(mob)) {burns.remove(key);continue;}
            if(now>=b.getLong("next")) {
                damage(level,mob,fireSource(level),b.getDouble("damage"),BlockPos.of(b.getLong("origin")),b.getUUID("identity"),null);
                b.putLong("next",now+20);
                level.sendParticles(ParticleTypes.SMALL_FLAME,mob.getX(),mob.getY()+.8,mob.getZ(),2,.2,.3,.2,0);
            }
        }
        if(burns.isEmpty())root.remove("cd_burns");
    }
    public static boolean turretArrow(AbstractArrow arrow) { return arrow.getTags().contains(BasicTurretBlockEntity.ARROW_TAG); }
    public static boolean canArrowHit(AbstractArrow arrow,Entity victim) { return isTarget(victim) && !arrow.getPersistentData().getBoolean("hit_"+victim.getUUID()); }
    public static void arrowHit(AbstractArrow arrow,LivingEntity victim,Vec3 point,Runnable effects) {
        // Minecraft owns this level; borrowers must not close it.
        //noinspection resource
        if(!(arrow.level() instanceof ServerLevel level))return;
        CompoundTag tag=arrow.getPersistentData();tag.putBoolean("hit_"+victim.getUUID(),true);
        BlockPos origin=BlockPos.of(tag.getLong("cd_origin"));UUID identity=tag.hasUUID("cd_turret")?tag.getUUID("cd_turret"):new UUID(0,0);
        double amount=tag.contains("cd_damage")?tag.getDouble("cd_damage"):4;
        damage(level,victim,level.damageSources().arrow(arrow,null),amount,origin,identity,effects);
        int pierce=tag.getInt("cd_pierce"),bounce=tag.getInt("cd_ricochet");
        if(pierce>0) {tag.putInt("cd_pierce",pierce-1);tag.putDouble("cd_damage",amount*.8);return;}
        if(bounce>0) {
            Mob next=null;double nearest=36;
            for(Mob m:level.getEntitiesOfClass(Mob.class,new AABB(point,point).inflate(6),m->canArrowHit(arrow,m))) {
                double distance=m.getBoundingBox().getCenter().distanceToSqr(point);
                if(distance<nearest && clear(level,point,m.getBoundingBox().getCenter())) {next=m;nearest=distance;}
            }
            if(next!=null) {
                Vec3 aim=aim(point,next,2.4,.05);
                if(aim!=null) {
                    tag.putInt("cd_ricochet",bounce-1);tag.putDouble("cd_damage",amount*.75);
                    // The current tick already checked the old straight segment. Defer the new segment
                    // until next tick, otherwise a close target is teleported through without collision.
                    Vec3 velocity=aim.scale(2.4);
                    tag.putBoolean("cd_bouncePending",true);tag.putDouble("cd_bounceX",velocity.x);tag.putDouble("cd_bounceY",velocity.y);tag.putDouble("cd_bounceZ",velocity.z);
                    arrow.setPos(point);arrow.setDeltaMovement(Vec3.ZERO);((TurretArrowAccess)arrow).cd$setPierceLevel((byte)0);arrow.hasImpulse=true;return;
                }
            }
        }
        arrow.discard();
    }
}
