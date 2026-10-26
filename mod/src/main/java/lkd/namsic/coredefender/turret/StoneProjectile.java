package lkd.namsic.coredefender.turret;

import org.jspecify.annotations.Nullable;

import lkd.namsic.coredefender.registry.ModTurrets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

import java.util.UUID;

/**
 * Real slow projectile. Explosions are damage queries, never world explosions or impulse sources.
 */
public class StoneProjectile extends ThrowableItemProjectile {
    private double damage = 14, radius = 3, bonus = 1, fragmentRadius = 1;
    private int fragments;
    private boolean fragment;
    private BlockPos origin = BlockPos.ZERO;
    private UUID identity = new UUID(0, 0);

    public StoneProjectile(EntityType<? extends StoneProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBBLESTONE;
    }

    @Override
    protected double getDefaultGravity() {
        return .05;
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return (!this.fragment || this.tickCount >= 4) && super.canHitEntity(entity) && TurretCombat.isTarget(entity);
    }

    public void configure(BasicTurretBlockEntity turret, TurretStats stats, boolean deep, int amplifier, boolean fragment) {
        this.origin = turret.getBlockPos();
        this.identity = turret.identity;
        this.fragment = fragment;
        this.damage = stats.damage(deep, amplifier);
        this.radius = stats.radius(deep);
        this.bonus = 1 + stats.damageBonus() + (amplifier == 2 ? .25 : 0);
        this.fragments = stats.fragments();
        this.fragmentRadius = 1 + stats.radiusBonus();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Vec3 point = result.getEntity().getBoundingBox().inflate(.3).clip(this.position(), this.position().add(this.getDeltaMovement())).orElse(result.getEntity().getBoundingBox().getCenter());
        this.explode(point, result.getEntity());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        this.explode(result.getLocation().add(Vec3.atLowerCornerOf(result.getDirection().getNormal()).scale(.025)), null);
    }

    private void explode(Vec3 point, @Nullable Entity direct) {
        // Minecraft owns this level; borrowers must not close it.
        //noinspection resource
        if (!(this.level() instanceof ServerLevel world) || this.isRemoved()) {
            return;
        }

        for (Mob victim : world.getEntitiesOfClass(Mob.class, new AABB(point, point).inflate(this.radius), TurretCombat::isTarget)) {
            double distance = victim.getBoundingBox().getCenter().distanceTo(point);
            if (distance > this.radius || !TurretCombat.clear(world, point, victim.getBoundingBox().getCenter())) {
                continue;
            }

            double amount = this.damage * Math.max(0, 1 - distance / this.radius) + (victim == direct && !this.fragment ? 4 * this.bonus : 0);
            TurretCombat.damage(world, victim, world.damageSources().explosion(this, null), amount, this.origin, this.identity, null);
        }

        world.sendParticles(ParticleTypes.EXPLOSION, point.x, point.y, point.z, this.fragment ? 1 : 3, .2, .2, .2, 0);
        world.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, this.fragment ? .25F : .8F, this.fragment ? 1.7F : 1.2F);
        for (int i = 0; i < this.fragments && !this.fragment; i++) {
            StoneProjectile child = new StoneProjectile(ModTurrets.STONE_PROJECTILE.get(), world);
            child.origin = this.origin;
            child.identity = this.identity;
            child.fragment = true;
            child.damage = 4 * this.bonus;
            child.radius = this.fragmentRadius;
            child.bonus = this.bonus;
            child.setItem(new ItemStack(Items.FLINT));
            child.setPos(point.add(0, .1, 0));
            double angle = 2 * Math.PI * (i + world.random.nextDouble()) / this.fragments, distance = 2 + world.random.nextDouble() * 2;
            child.setDeltaMovement(-Math.sin(angle) * distance / 10, .25, Math.cos(angle) * distance / 10);
            world.addFreshEntity(child);
        }

        this.discard();
    }

    // Minecraft owns this level; borrowers must not close it.
    @SuppressWarnings("resource")
    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > 200) {
            this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Damage", this.damage);
        tag.putDouble("Radius", this.radius);
        tag.putDouble("Bonus", this.bonus);
        tag.putDouble("FragmentRadius", this.fragmentRadius);
        tag.putInt("Fragments", this.fragments);
        tag.putBoolean("Fragment", this.fragment);
        tag.putLong("Origin", this.origin.asLong());
        tag.putUUID("Turret", this.identity);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.damage = tag.getDouble("Damage");
        this.radius = tag.getDouble("Radius");
        this.bonus = tag.getDouble("Bonus");
        this.fragmentRadius = tag.getDouble("FragmentRadius");
        this.fragments = tag.getInt("Fragments");
        this.fragment = tag.getBoolean("Fragment");
        this.origin = BlockPos.of(tag.getLong("Origin"));
        if (tag.hasUUID("Turret")) {
            this.identity = tag.getUUID("Turret");
        }
    }
}
