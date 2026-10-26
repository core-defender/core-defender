package lkd.namsic.coredefender.turret;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import lombok.Getter;
import net.minecraft.network.Connection;

import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.registry.*;
import lkd.namsic.coredefender.wall.WallManager;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraft.sounds.*;
import net.neoforged.neoforge.items.*;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.*;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.*;

/**
 * Shared resource, targeting and automation controller for all turret types.
 */
public class BasicTurretBlockEntity extends BlockEntity implements MenuProvider {
    public static final String ARROW_TAG = "cd_turret_arrow";
    public static final TagKey<Item> STONE = TagKey.create(Registries.ITEM, ResourceLocation.parse("coredefender:turret_stones"));
    public static final TagKey<Item> DEEP = TagKey.create(Registries.ITEM, ResourceLocation.parse("coredefender:turret_deepslate"));
    public static final String[] STATUS = {"대기: 유효한 적 없음", "공격 준비", "탄약 부족", "연료 부족", "종자유 부족", "조준 중", "장전 / 휴지기", "레드스톤 정지", "디펜스 차원 / 성벽 내부 전용", "증폭 재료 대기", "사선 차단", "성벽 가장자리에서 바깥 방향으로 설치 필요"};
    // 0..8 ammunition (stone: 0..7 stone, 8 coal); 9..10 modules; 11 amplifier; 12 container; 13 output.
    private final OilSpill oilSpill = new OilSpill();
    private boolean visualDirty = true;
    @Getter
    public final ItemStackHandler inventory = new ItemStackHandler(14) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int kind = BasicTurretBlockEntity.this.kind();
            if (slot < 9) {
                return kind == 0 ? stack.is(Items.ARROW) || stack.is(Items.TIPPED_ARROW) || stack.is(Items.SPECTRAL_ARROW) : kind == 1 && (slot == 8 ? fuelValue(stack) > 0 : stack.is(STONE) || stack.is(DEEP));
            }

            if (slot < 11) {
                return stack.getItem() instanceof TurretModule module && module.supports(kind);
            }

            if (slot == 11) {
                return kind == 1 && (stack.is(Items.BLAZE_POWDER) || stack.is(Items.GUNPOWDER));
            }

            if (slot == 12) {
                if (kind < 2) {
                    return false;
                }

                if (stack.is(Items.GLASS_BOTTLE) || stack.is(ModItems.SEED_OIL_BOTTLE.get())) {
                    return true;
                }

                if (FluidUtil.getFluidHandler(stack.copyWithCount(1)).isEmpty()) {
                    return false;
                }

                FluidStack fluid = BasicTurretBlockEntity.this.containerContents(stack);
                return fluid.isEmpty() || BasicTurretBlockEntity.this.oil.isFluidValid(fluid) || BasicTurretBlockEntity.this.lava.isFluidValid(fluid);
            }

            return false;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 9 || slot == 10 ? 1 : 64;
        }

        @Override
        protected void onContentsChanged(int slot) {
            BasicTurretBlockEntity.this.dirty();
            if ((slot == 9 || slot == 10) && BasicTurretBlockEntity.this.level != null && !BasicTurretBlockEntity.this.level.isClientSide) {
                BasicTurretBlockEntity.this.reloadStarted = BasicTurretBlockEntity.this.level.getGameTime();
                BasicTurretBlockEntity.this.burstTicks = 0;
            }
        }
    };
    public final FluidTank oil = new FluidTank(9000, f -> BuiltInRegistries.FLUID.getKey(f.getFluid()).toString().equals("createaddition:seed_oil")) {
        @Override
        protected void onContentsChanged() {
            BasicTurretBlockEntity.this.dirty();
        }
    };
    public final FluidTank lava = new FluidTank(1000, f -> this.kind() >= TurretStats.FLAME && f.getFluid() == Fluids.LAVA) {
        @Override
        protected void onContentsChanged() {
            BasicTurretBlockEntity.this.dirty();
        }
    };
    public final int[] faces = new int[6]; // disabled, input, output, both
    public final int[] limits = {576, 64, 64, 9000, 1000}; // ammo total, coal, amplifier, oil, lava
    public int priority = 1, redstone;
    public boolean lockTarget = true, stopWithoutAmp, usedAmp;
    public int status, fuelShots, ampShots, ampType, burstTicks;
    private @Nullable Mob trackedTarget;
    private boolean blockedTargets;
    private float yaw, pitch, previousYaw, previousPitch;
    private long receivedAt;
    private boolean hasVisualState;
    @Getter
    private boolean loaded;
    @Getter
    private long reloadStarted;
    @Getter
    private long lastShot = -1000;
    private final double[] damageHistory = new double[200];
    private final long[] damageTimes = new long[200];
    public UUID identity = UUID.randomUUID();

    public BasicTurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BASIC_TURRET.get(), pos, state);
    }

    public int kind() {
        return this.getBlockState().is(ModBlocks.OIL_TURRET.get()) ? 3 : this.getBlockState().is(ModBlocks.STONE_TURRET.get()) ? 1 : this.getBlockState().is(ModBlocks.FLAME_TURRET.get()) ? 2 : 0;
    }

    public void orientOil(Direction direction) {
        if (this.kind() == TurretStats.OIL) {
            this.yaw = Mth.wrapDegrees(direction.toYRot());
            this.previousYaw = this.yaw;
            this.pitch = 0;
            this.previousPitch = 0;
            this.dirty();
        }
    }

    public float renderYaw(float partial) {
        return Mth.rotLerp(this.interpolation(partial), this.previousYaw, this.yaw);
    }

    public float renderPitch(float partial) {
        return Mth.lerp(this.interpolation(partial), this.previousPitch, this.pitch);
    }

    private float interpolation(float partial) {
        return this.level == null ? 1 : Mth.clamp((this.level.getGameTime() - this.receivedAt + partial) / 4F, 0, 1);
    }

    private void dirty() {
        this.setChanged();
        this.visualDirty = true;
    }

    public int arrowCount() {
        return this.count(0, this.kind() == 1 ? 8 : 9);
    }

    private int count(int start, int end) {
        int total = 0;
        for (int i = start; i < end; i++) {
            total += this.inventory.getStackInSlot(i).getCount();
        }

        return total;
    }

    private int ammoSlot() {
        for (int i = 0; i < (this.kind() == 1 ? 8 : 9); i++) {
            if (!this.inventory.getStackInSlot(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    public boolean deep() {
        int slot = this.ammoSlot();
        return this.kind() == 1 && slot >= 0 && this.inventory.getStackInSlot(slot).is(DEEP);
    }

    public static int fuelValue(ItemStack s) {
        return s.is(Items.COAL_BLOCK) ? 72 : s.is(Items.COAL) || s.is(Items.CHARCOAL) ? 8 : 0;
    }

    public TurretStats stats() {
        int[] v = new int[11];
        for (int i = 9; i <= 10; i++) {
            if (this.inventory.getStackInSlot(i).getItem() instanceof TurretModule m && m.supports(this.kind())) {
                v[m.effect == 9 ? 6 : m.effect] += m.grade;
            }
        }

        return new TurretStats(this.kind(), v[0] * .15, v[1] * .1, v[2], v[3], v[4], v[5] * .3, v[6] * .15, v[7] * .15, v[8] * 2, v[6] * .5, v[6] * 45, v[10] * .25);
    }

    public int amplifier() {
        if (this.kind() == TurretStats.OIL) {
            return this.lava.getFluidAmount() >= 10 ? 3 : 0;
        }

        if (this.kind() == 2) {
            return this.lava.getFluidAmount() > 0 ? 3 : 0;
        }

        if (this.kind() != 1) {
            return 0;
        }

        if (this.ampShots > 0) {
            return this.ampType;
        }

        ItemStack s = this.inventory.getStackInSlot(11);
        return s.is(Items.BLAZE_POWDER) ? 1 : s.is(Items.GUNPOWDER) ? 2 : 0;
    }

    public boolean ampEmptyWarning() {
        return this.kind() != 0 && this.usedAmp && this.amplifier() == 0;
    }

    public boolean outputBlocked;

    public void recordDamage(double amount) {
        if (this.level == null || amount <= 0) {
            return;
        }

        long now = this.level.getGameTime();
        int index = (int) (now % 200);
        if (this.damageTimes[index] != now) {
            this.damageTimes[index] = now;
            this.damageHistory[index] = 0;
        }

        this.damageHistory[index] += amount;
    }

    public double actualDps() {
        if (this.level == null) {
            return 0;
        }

        double sum = 0;
        long now = this.level.getGameTime();
        for (int i = 0; i < 200; i++) {
            if (now - this.damageTimes[i] < 200 && now >= this.damageTimes[i]) {
                sum += this.damageHistory[i];
            }
        }

        return sum / 10;
    }

    public Vec3 pivot() {
        return Vec3.atCenterOf(this.worldPosition).add(0, .8, 0);
    }

    protected boolean operatingArea(ServerLevel world) {
        return DefenseDimension.is(world) && WallManager.state(world).geometry().isInterior(this.worldPosition.getX(), this.worldPosition.getZ());
    }

    public static void serverTick(Level world, BlockPos pos, BlockState state, BasicTurretBlockEntity t) {
        if (!(world instanceof ServerLevel level)) {
            return;
        }

        long now = level.getGameTime();
        if (now % 5 == 0) {
            t.processContainer();
        }

        if (t.kind() == TurretStats.OIL) {
            t.oilSpill.tick(level, WallManager.state(level).geometry(), t);
        }

        TurretStats s = t.stats();
        int amp = t.amplifier();
        if (amp != 0 && !t.usedAmp) {
            t.usedAmp = true;
            t.dirty();
        }

        boolean wasLoaded = t.loaded;
        t.loaded = t.kind() == TurretStats.OIL ? t.oil.getFluidAmount() >= TurretStats.OIL_PER_POUR : t.kind() == 2 ? t.oil.getFluidAmount() >= TurretStats.FLAME_OIL_PER_TICK : t.arrowCount() > 0;
        if (!wasLoaded && t.loaded && t.kind() < 2) {
            t.reloadStarted = now;
            t.dirty();
        }

        int before = t.status;
        if (!t.operatingArea(level)) {
            t.status = 8;
        } else if (t.redstone == 1 && !level.hasNeighborSignal(pos) || t.redstone == 2 && level.hasNeighborSignal(pos)) {
            t.status = 7;
        } else if (!t.loaded) {
            t.status = t.kind() >= 2 ? 4 : 2;
        } else if (t.kind() == 1 && t.fuelShots == 0 && fuelValue(t.inventory.getStackInSlot(8)) == 0) {
            t.status = 3;
        } else if (t.kind() != 0 && t.stopWithoutAmp && amp == 0) {
            t.status = 9;
        } else if (t.kind() == TurretStats.OIL) {
            t.pourOil(level, s, now);
        } else {
            double range = s.range(t.deep(), amp);
            boolean valid = t.trackedTarget != null && t.valid(t.trackedTarget, range);
            if (!valid || (!t.lockTarget && t.burstTicks == 0 && now - t.reloadStarted >= s.interval(amp))) {
                if (now % 4 == 0 || valid) {
                    t.trackedTarget = t.targets(level, range).stream().findFirst().orElse(null);
                }
            }

            Mob target = t.trackedTarget;
            if (t.kind() == 2 && t.burstTicks > 0) {
                t.flame(level, s, amp, target);
            } else if (target == null || !t.valid(target, range)) {
                t.status = t.blockedTargets ? 10 : 0;
            } else {
                Vec3 direction = t.kind() == 2 ? target.getBoundingBox().getCenter().subtract(t.pivot()).normalize()
                    : TurretCombat.aim(t.pivot(), target, s.projectileSpeed(amp), .05);
                if (direction == null) {
                    t.status = 10;
                    t.trackedTarget = null;
                } else {
                    float wantedYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
                    float wantedPitch = (float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance()));
                    float yawStep = (float) ((t.kind() == 1 ? 4.5 : 9) * (1 + s.aimBonus()));
                    float pitchStep = (float) ((t.kind() == 1 ? 3 : 6) * (1 + s.aimBonus()));
                    t.yaw = t.kind() == 2 ? wantedYaw : Mth.approachDegrees(t.yaw, wantedYaw, yawStep);
                    t.pitch = t.kind() == 2 ? 0 : Mth.approachDegrees(t.pitch, wantedPitch, pitchStep);
                    boolean aligned = t.kind() == 2 || Math.abs(Mth.wrapDegrees(t.yaw - wantedYaw)) <= 5 && Math.abs(t.pitch - wantedPitch) <= 5;
                    if (!aligned) {
                        t.status = 5;
                    } else if (now - t.reloadStarted < s.interval(amp)) {
                        t.status = 6;
                    } else if (t.kind() == 2) {
                        t.burstTicks = 10;
                        t.lastShot = now;
                        t.reloadStarted = now;
                        t.flame(level, s, amp, target);
                    } else {
                        t.shoot(level, s, amp, target, direction);
                    }
                }
            }
        }

        if (t.status == 7 || t.status == 8 || t.status == 9 || t.status == 4) {
            t.burstTicks = 0;
        }

        if (t.status != before || now % 4 == 0) {
            t.visualDirty = true;
        }

        if (t.visualDirty) {
            level.sendBlockUpdated(pos, state, state, 2);
            t.visualDirty = false;
        }
    }

    private boolean valid(Mob mob, double range) {
        return mob.isAlive() && TurretCombat.isTarget(mob) && mob.distanceToSqr(this.pivot()) <= range * range;
    }

    // Project conventions require blocks for every case when a case spans multiple lines.
    @SuppressWarnings("RedundantLabeledSwitchRuleCodeBlock")
    private List<Mob> targets(ServerLevel world, double range) {
        List<Mob> result = world.getEntitiesOfClass(Mob.class, new AABB(this.worldPosition).inflate(range), m -> this.valid(m, range));
        Comparator<Mob> comparator = switch (this.priority) {
            case 0 -> {
                yield Comparator.comparingDouble(m -> m.distanceToSqr(this.pivot()));
            }
            case 2 -> {
                yield Comparator.comparingDouble(Mob::getHealth);
            }
            case 3 -> {
                yield Comparator.comparingDouble(m -> -m.getHealth());
            }
            default -> {
                yield Comparator.comparingDouble(m -> WallManager.state(world).geometry().distanceToWall(m.getX(), m.getZ()));
            }
        };
        result.sort(comparator.thenComparingInt(Mob::getId));
        // Do not lock permanently onto an unreachable enemy in front of a usable target.
        ArrayList<Mob> reachable = new ArrayList<>();
        TurretStats stats = this.stats();
        int wanted = this.kind() == 0 ? 1 + stats.multishot() : 1;
        for (Mob mob : result) {
            boolean clear = this.kind() == 2 ? TurretCombat.clear(world, this.pivot(), mob.getBoundingBox().getCenter())
                : TurretCombat.clearArc(world, this.pivot(), mob, stats.projectileSpeed(this.amplifier()));
            if (clear) {
                reachable.add(mob);
            }

            if (reachable.size() >= wanted) {
                break;
            }
        }

        this.blockedTargets = !result.isEmpty() && reachable.isEmpty();
        return reachable;
    }

    private void shoot(ServerLevel world, TurretStats s, int amp, Mob target, Vec3 direction) {
        Vec3 launch = this.pivot().add(TurretPose.muzzleOffset(this.yaw, this.pitch));
        if (!TurretCombat.clear(world, this.pivot(), launch) || !TurretCombat.clearArc(world, launch, target, s.projectileSpeed(amp))) {
            this.status = 10;
            this.trackedTarget = null;
            return;
        }

        if (this.kind() == 0) {
            List<Mob> targets = this.targets(world, s.range(false, 0));
            targets.remove(target);
            targets.addFirst(target);
            int fired = 0;
            for (int shot = 0; shot < 1 + s.multishot(); shot++) {
                if (this.ammoSlot() < 0) {
                    break;
                }

                Mob victim = targets.get(shot % targets.size());
                Vec3 aim = TurretCombat.aim(launch, victim, 2.4, .05);
                if (aim == null || !TurretCombat.clearArc(world, launch, victim, 2.4)) {
                    continue;
                }

                ItemStack ammo = this.inventory.extractItem(this.ammoSlot(), 1, false);
                AbstractArrow arrow = ammo.is(Items.SPECTRAL_ARROW)
                    ? new SpectralArrow(world, launch.x, launch.y, launch.z, ammo, null)
                    : new Arrow(world, launch.x, launch.y, launch.z, ammo, null);
                arrow.setPos(launch);
                arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                arrow.addTag(ARROW_TAG);
                // Vanilla piercing loop remains enabled; our hit handler controls the actual hit budget.
                if (world.random.nextFloat() < .15) {
                    double scatter = Math.toRadians(4 + world.random.nextDouble() * 6), az = world.random.nextDouble() * Math.PI * 2;
                    Vec3 side = aim.cross(new Vec3(0, 1, 0)).normalize();
                    if (side.lengthSqr() < .01) {
                        side = new Vec3(1, 0, 0);
                    }

                    aim = aim.scale(Math.cos(scatter)).add(side.scale(Math.sin(scatter) * Math.cos(az))).add(aim.cross(side).scale(Math.sin(scatter) * Math.sin(az))).normalize();
                }

                arrow.shoot(aim.x, aim.y, aim.z, 2.4F, 0);
                CompoundTag data = arrow.getPersistentData();
                data.putDouble("cd_damage", s.damage(false, 0));
                data.putInt("cd_pierce", s.pierce());
                data.putInt("cd_ricochet", s.ricochet());
                data.putLong("cd_origin", this.worldPosition.asLong());
                data.putUUID("cd_turret", this.identity);
                world.addFreshEntity(arrow);
                fired++;
            }

            if (fired == 0) {
                return;
            }

            world.playSound(null, this.worldPosition, SoundEvents.CROSSBOW_SHOOT, SoundSource.BLOCKS, .6F, 1.2F);
        } else {
            boolean deep = this.deep();
            if (this.fuelShots == 0) {
                this.fuelShots = fuelValue(this.inventory.getStackInSlot(8));
                this.inventory.extractItem(8, 1, false);
            }

            if (amp != 0 && this.ampShots == 0) {
                this.ampType = amp;
                this.ampShots = 16;
                this.inventory.extractItem(11, 1, false);
            }

            ItemStack ammo = this.inventory.extractItem(this.ammoSlot(), 1, false);
            this.fuelShots--;
            if (this.ampShots > 0) {
                this.ampShots--;
            }

            StoneProjectile stone = new StoneProjectile(ModTurrets.STONE_PROJECTILE.get(), world);
            stone.setPos(launch);
            stone.setItem(ammo);
            stone.configure(this, s, deep, amp, false);
            Vec3 aim = TurretCombat.aim(launch, target, s.projectileSpeed(amp), .05);
            if (aim == null) {
                aim = direction;
            }

            stone.setDeltaMovement(aim.scale(s.projectileSpeed(amp)));
            world.addFreshEntity(stone);
            world.playSound(null, this.worldPosition, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, .8F, .6F);
        }

        this.lastShot = world.getGameTime();
        this.reloadStarted = this.lastShot;
        this.status = 1;
        this.dirty();
    }

    private void flame(ServerLevel world, TurretStats s, int amp, @Nullable Mob target) {
        if (this.oil.getFluidAmount() < TurretStats.FLAME_OIL_PER_TICK) {
            this.burstTicks = 0;
            this.status = 4;
            return;
        }

        if (target != null && target.isAlive()) {
            Vec3 d = target.position().subtract(this.pivot());
            this.yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        }

        this.oil.drain(TurretStats.FLAME_OIL_PER_TICK, IFluidHandler.FluidAction.EXECUTE);
        if (amp == 3) {
            this.lava.drain(1, IFluidHandler.FluidAction.EXECUTE);
        }

        int elapsed = 10 - this.burstTicks;
        TurretCombat.flameParticles(world, this.pivot(), this.yaw, s.range(false, amp), s.angle());
        if (elapsed == 0 || elapsed == 3 || elapsed == 6 || elapsed == 9) {
            for (Mob m : world.getEntitiesOfClass(Mob.class, new AABB(this.worldPosition).inflate(s.range(false, amp), 3, s.range(false, amp)), TurretCombat::isTarget)) {
                if (TurretCombat.inCone(this.pivot(), m.getBoundingBox().getCenter(), this.yaw, s.range(false, amp), s.angle()) && TurretCombat.clear(world, this.pivot(), m.getBoundingBox().getCenter())) {
                    if (TurretCombat.fireImmune(m)) {
                        continue;
                    }

                    TurretCombat.damage(world, m, TurretCombat.fireSource(world), s.damage(false, amp), this.worldPosition, this.identity, null);
                    TurretCombat.burn(m, this, s.burn(amp));
                }
            }
        }

        if (elapsed == 0) {
            world.playSound(null, this.worldPosition, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, .5F, 1.2F);
        }

        this.burstTicks--;
        this.status = 1;
        this.dirty();
    }

    private void pourOil(ServerLevel world, TurretStats stats, long now) {
        if (now - this.reloadStarted < stats.interval(0)) {
            this.status = 6;
            return;
        }

        if (now % 4 != 0) {
            return;
        }

        Vec3 outlet = OilSpill.outlet(world, WallManager.state(world).geometry(), this.worldPosition, this.yaw);
        if (outlet == null) {
            this.status = 11;
            return;
        }

        int amp = this.amplifier();
        Direction facing = Direction.fromYRot(this.yaw);
        Vec3 sensor = outlet.add(-facing.getStepX() * .5, 0, -facing.getStepZ() * .5);
        this.trackedTarget = world.getEntitiesOfClass(Mob.class, new AABB(outlet, outlet).inflate(stats.detectionRange(), 8, stats.detectionRange()),
            mob -> TurretCombat.isTarget(mob) && !TurretCombat.fireImmune(mob)
                && !WallManager.state(world).geometry().isInterior(mob.getBlockX(), mob.getBlockZ())
                && TurretCombat.inCone(sensor, new Vec3(mob.getX(), sensor.y, mob.getZ()), this.yaw, stats.detectionRange(), stats.angle())).stream().findFirst().orElse(null);
        if (this.trackedTarget == null) {
            this.status = 0;
            return;
        }

        this.oil.drain(TurretStats.OIL_PER_POUR, IFluidHandler.FluidAction.EXECUTE);
        if (amp == 3) {
            this.lava.drain(10, IFluidHandler.FluidAction.EXECUTE);
        }

        this.oilSpill.pour(outlet, now, stats, amp);
        this.lastShot = now;
        this.reloadStarted = now;
        this.status = 1;
        Vec3 start = this.pivot();
        for (int i = 0; i <= 12; i++) {
            Vec3 point = start.lerp(outlet.add(0, .1, 0), i / 12.0);
            world.sendParticles(ParticleTypes.DRIPPING_LAVA, point.x, point.y, point.z, 2, .07, .02, .07, 0);
        }

        world.playSound(null, this.worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, .6F, .7F);
        this.dirty();
    }

    private void processContainer() {
        this.outputBlocked = false;
        if (this.kind() < 2) {
            return;
        }

        ItemStack input = this.inventory.getStackInSlot(12);
        if (input.isEmpty()) {
            return;
        }

        ItemStack result;
        FluidStack fluid;
        Optional<IFluidHandlerItem> handler = FluidUtil.getFluidHandler(input.copyWithCount(1));
        if (input.is(ModItems.SEED_OIL_BOTTLE.get())) {
            fluid = new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse("createaddition:seed_oil")), 250);
            result = new ItemStack(Items.GLASS_BOTTLE);
        } else if (handler.isPresent()) {
            fluid = handler.get().drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
            if (fluid.isEmpty()) {
                return;
            }

            handler.get().drain(fluid, IFluidHandler.FluidAction.EXECUTE);
            result = handler.get().getContainer();
        } else {
            return;
        }

        FluidTank tank = this.oil.isFluidValid(fluid) ? this.oil : this.lava.isFluidValid(fluid) ? this.lava : null;
        if (tank == null || tank.fill(fluid, IFluidHandler.FluidAction.SIMULATE) != fluid.getAmount()) {
            return;
        }

        ItemStack out = this.inventory.getStackInSlot(13);
        if (!out.isEmpty() && (!ItemStack.isSameItemSameComponents(out, result) || out.getCount() + result.getCount() > out.getMaxStackSize())) {
            this.outputBlocked = true;
            return;
        }

        tank.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        this.inventory.extractItem(12, 1, false);
        this.inventory.setStackInSlot(13, out.isEmpty() ? result : out.copyWithCount(out.getCount() + result.getCount()));
    }

    public IItemHandler itemPort(@Nullable Direction side) {
        return new IItemHandler() {
            public int getSlots() {
                return 14;
            }

            public ItemStack getStackInSlot(int slot) {
                return BasicTurretBlockEntity.this.inventory.getStackInSlot(slot);
            }

            public int getSlotLimit(int slot) {
                return BasicTurretBlockEntity.this.inventory.getSlotLimit(slot);
            }

            public boolean isItemValid(int slot, ItemStack stack) {
                return BasicTurretBlockEntity.this.inputAllowed(side) && slot != 9 && slot != 10 && BasicTurretBlockEntity.this.inventory.isItemValid(slot, stack);
            }

            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!this.isItemValid(slot, stack)) {
                    return stack;
                }

                int room = slot < 9 ? (BasicTurretBlockEntity.this.kind() == 1 && slot == 8 ? BasicTurretBlockEntity.this.limits[1] - BasicTurretBlockEntity.this.count(8, 9) : BasicTurretBlockEntity.this.limits[0] - BasicTurretBlockEntity.this.arrowCount()) : slot == 11 ? BasicTurretBlockEntity.this.limits[2] - BasicTurretBlockEntity.this.count(11, 12) : 64;
                if (slot == 12) {
                    FluidStack incoming = BasicTurretBlockEntity.this.containerContents(stack);
                    if (!incoming.isEmpty()) {
                        FluidTank tank = BasicTurretBlockEntity.this.oil.isFluidValid(incoming) ? BasicTurretBlockEntity.this.oil : BasicTurretBlockEntity.this.lava;
                        ItemStack waiting = BasicTurretBlockEntity.this.inventory.getStackInSlot(12);
                        FluidStack pending = BasicTurretBlockEntity.this.containerContents(waiting);
                        int reserved = !pending.isEmpty() && pending.getFluid() == incoming.getFluid() ? pending.getAmount() * waiting.getCount() : 0;
                        room = Math.max(0, (BasicTurretBlockEntity.this.limits[tank == BasicTurretBlockEntity.this.oil ? 3 : 4] - tank.getFluidAmount() - reserved) / incoming.getAmount());
                    }
                }

                int amount = Math.clamp(room, 0, stack.getCount());
                if (amount == 0) {
                    return stack;
                }

                ItemStack remainder = BasicTurretBlockEntity.this.inventory.insertItem(slot, stack.copyWithCount(amount), simulate);
                return stack.copyWithCount(stack.getCount() - amount + remainder.getCount());
            }

            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (!BasicTurretBlockEntity.this.outputAllowed(side) || slot != 13) {
                    return ItemStack.EMPTY;
                }

                ItemStack stack = BasicTurretBlockEntity.this.inventory.getStackInSlot(13);
                if (stack.is(ModItems.SEED_OIL_BOTTLE.get()) || !FluidUtil.getFluidContained(stack.copyWithCount(1)).orElse(FluidStack.EMPTY).isEmpty()) {
                    return ItemStack.EMPTY;
                }

                return BasicTurretBlockEntity.this.inventory.extractItem(slot, amount, simulate);
            }
        };
    }

    private FluidStack containerContents(ItemStack stack) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }

        if (stack.is(ModItems.SEED_OIL_BOTTLE.get())) {
            return new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse("createaddition:seed_oil")), 250);
        }

        return FluidUtil.getFluidContained(stack.copyWithCount(1)).orElse(FluidStack.EMPTY);
    }

    private boolean inputAllowed(@Nullable Direction side) {
        return side != null && (BasicTurretBlockEntity.this.faces[side.ordinal()] == 1 || BasicTurretBlockEntity.this.faces[side.ordinal()] == 3);
    }

    private boolean outputAllowed(@Nullable Direction side) {
        return side != null && (BasicTurretBlockEntity.this.faces[side.ordinal()] == 2 || BasicTurretBlockEntity.this.faces[side.ordinal()] == 3);
    }

    public IFluidHandler fluidPort(@Nullable Direction side) {
        return new IFluidHandler() {
            public int getTanks() {
                return 2;
            }

            public FluidStack getFluidInTank(int tank) {
                return (tank == 0 ? BasicTurretBlockEntity.this.oil : BasicTurretBlockEntity.this.lava).getFluid().copy();
            }

            public int getTankCapacity(int tank) {
                return tank == 0 ? 9000 : 1000;
            }

            public boolean isFluidValid(int tank, FluidStack fluid) {
                return BasicTurretBlockEntity.this.kind() >= 2 && (tank == 0 ? BasicTurretBlockEntity.this.oil : BasicTurretBlockEntity.this.lava).isFluidValid(fluid);
            }

            public int fill(FluidStack fluid, FluidAction action) {
                if (!BasicTurretBlockEntity.this.inputAllowed(side) || BasicTurretBlockEntity.this.kind() < 2) {
                    return 0;
                }

                FluidTank tank = BasicTurretBlockEntity.this.oil.isFluidValid(fluid) ? BasicTurretBlockEntity.this.oil : BasicTurretBlockEntity.this.lava.isFluidValid(fluid) ? BasicTurretBlockEntity.this.lava : null;
                if (tank == null) {
                    return 0;
                }

                int room = Math.max(0, BasicTurretBlockEntity.this.limits[tank == BasicTurretBlockEntity.this.oil ? 3 : 4] - tank.getFluidAmount());
                return tank.fill(fluid.copyWithAmount(Math.min(room, fluid.getAmount())), action);
            }

            public FluidStack drain(FluidStack stack, FluidAction action) {
                return FluidStack.EMPTY;
            }

            public FluidStack drain(int amount, FluidAction action) {
                return FluidStack.EMPTY;
            }
        };
    }

    public void action(int id) {
        if (id == 0) {
            this.priority = (this.priority + 1) % 4;
        } else if (id == 1) {
            this.lockTarget = !this.lockTarget;
        } else if (id == 2) {
            this.redstone = (this.redstone + 1) % 3;
        } else if (id == 3) {
            this.stopWithoutAmp = !this.stopWithoutAmp;
        } else if (id >= 10 && id < 16) {
            this.faces[id - 10] = (this.faces[id - 10] + 1) % 4;
        } else if (id >= 30 && id < 40) {
            int role = (id - 30) / 2;
            int step = role >= 3 ? 250 : 16;
            int max = role == 0 ? (this.kind() == 1 ? 512 : 576) : role == 3 ? 9000 : role == 4 ? 1000 : 64;
            this.limits[role] = Mth.clamp(this.limits[role] + (id % 2 == 0 ? -step : step), 0, max);
        } else if (id == 50 || id == 51) {
            this.drainContainer(id == 50 ? this.oil : this.lava);
        }

        this.trackedTarget = null;
        this.dirty();
    }

    private void drainContainer(FluidTank tank) {
        // Manual container filling only; automatic fluid extraction is deliberately unavailable.
        ItemStack input = this.inventory.getStackInSlot(12);
        if (tank == this.oil && input.is(Items.GLASS_BOTTLE) && this.oil.getFluidAmount() >= 250) {
            ItemStack out = this.inventory.getStackInSlot(13), filled = new ItemStack(ModItems.SEED_OIL_BOTTLE.get());
            if (!out.isEmpty() && (!ItemStack.isSameItemSameComponents(out, filled) || out.getCount() >= out.getMaxStackSize())) {
                return;
            }

            this.oil.drain(250, IFluidHandler.FluidAction.EXECUTE);
            this.inventory.extractItem(12, 1, false);
            this.inventory.setStackInSlot(13, out.isEmpty() ? filled : out.copyWithCount(out.getCount() + 1));
            return;
        }

        FluidActionResult result = FluidUtil.tryFillContainer(input, tank, 1000, null, false);
        if (!result.isSuccess()) {
            return;
        }

        ItemStack filled = result.getResult(), out = this.inventory.getStackInSlot(13);
        if (!out.isEmpty() && (!ItemStack.isSameItemSameComponents(out, filled) || out.getCount() + filled.getCount() > out.getMaxStackSize())) {
            return;
        }

        FluidActionResult actual = FluidUtil.tryFillContainer(input, tank, 1000, null, true);
        if (!actual.isSuccess()) {
            return;
        }

        this.inventory.extractItem(12, 1, false);
        this.inventory.setStackInSlot(13, out.isEmpty() ? actual.getResult() : out.copyWithCount(out.getCount() + actual.getResult().getCount()));
    }

    // Project conventions require blocks for every case when a case spans multiple lines.
    @SuppressWarnings("RedundantLabeledSwitchRuleCodeBlock")
    public int data(int i) {
        TurretStats s = this.stats();
        int amp = this.amplifier();
        if (i >= 21 && i < 27) {
            return this.faces[i - 21];
        }

        if (i >= 27 && i < 32) {
            return this.limits[i - 27];
        }

        return switch (i) {
            case 0 -> {
                yield this.kind();
            }
            case 1 -> {
                yield this.status;
            }
            case 2 -> {
                yield this.arrowCount();
            }
            case 3 -> {
                yield this.oil.getFluidAmount();
            }
            case 4 -> {
                yield this.lava.getFluidAmount();
            }
            case 5 -> {
                yield this.level == null ? 0 : Math.clamp(this.level.getGameTime() - this.reloadStarted, 0, s.interval(amp));
            }
            case 6 -> {
                yield s.interval(amp);
            }
            case 7 -> {
                yield this.trackedTarget == null ? -1 : this.trackedTarget.getId() & 65535;
            }
            case 8 -> {
                yield (int) Math.round(s.damage(this.deep(), amp) * 100);
            }
            case 9 -> {
                yield (int) Math.round(s.range(this.deep(), amp) * 100);
            }
            case 10 -> {
                yield (int) s.angle();
            }
            case 11 -> {
                yield (int) Math.round(s.radius(this.deep()) * 100);
            }
            case 12 -> {
                yield (int) Math.round(s.damage(this.deep(), amp) * (this.kind() >= 2 ? 4 : 1) * 20 / s.interval(amp) * 100);
            }
            case 13 -> {
                yield (int) Math.min(Integer.MAX_VALUE, Math.round(this.actualDps() * 100)) & 65535;
            }
            case 14 -> {
                yield this.priority;
            }
            case 15 -> {
                yield this.lockTarget ? 1 : 0;
            }
            case 16 -> {
                yield this.redstone;
            }
            case 17 -> {
                yield this.stopWithoutAmp ? 1 : 0;
            }
            case 18 -> {
                yield this.fuelShots;
            }
            case 19 -> {
                yield this.ampShots;
            }
            case 20 -> {
                yield amp;
            }
            case 32 -> {
                yield (this.ampEmptyWarning() ? 1 : 0) + (this.outputBlocked ? 2 : 0);
            }
            case 33 -> {
                yield this.burstTicks;
            }
            case 34 -> {
                yield (int) Math.round(s.burn(amp) * 100);
            }
            case 35 -> {
                yield s.fragments();
            }
            case 36 -> {
                yield s.pierce();
            }
            case 37 -> {
                yield s.multishot();
            }
            case 38 -> {
                yield s.ricochet();
            }
            case 39 -> {
                yield (int) Math.round(s.projectileSpeed(amp) * 100);
            }
            case 40 -> {
                yield (int) Math.round((this.kind() == 1 ? 90 : 180) * (1 + s.aimBonus()));
            }
            case 41 -> {
                yield (int) Math.round(s.damageBonus() * 100);
            }
            case 42 -> {
                yield (int) Math.round(s.speedBonus() * 100);
            }
            case 43 -> {
                yield this.deep() ? 1 : 0;
            }
            case 44 -> {
                yield this.trackedTarget == null ? -1 : this.trackedTarget.getId() >>> 16;
            }
            case 45 -> {
                yield (int) Math.min(Integer.MAX_VALUE, Math.round(this.actualDps() * 100)) >>> 16;
            }
            default -> {
                yield 0;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new TurretMenu(id, playerInventory, this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("yaw", this.yaw);
        tag.putFloat("pitch", this.pitch);
        tag.putBoolean("loaded", this.loaded);
        tag.putLong("reloadStarted", this.reloadStarted);
        tag.putLong("lastShot", this.lastShot);
        tag.putInt("status", this.status);
        tag.putBoolean("ampWarning", this.ampEmptyWarning());
        tag.putBoolean("outputBlocked", this.outputBlocked);
        tag.putInt("interval", this.stats().interval(this.amplifier()));
        tag.putDouble("range", this.stats().range(this.deep(), this.amplifier()));
        tag.putDouble("angle", this.stats().angle());
        return tag;
    }

    public int clientInterval = 16;
    public double clientRange = 16, clientAngle = 90;
    public boolean clientAmpWarning;

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        this.handleUpdateTag(packet.getTag(), registries);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        this.previousYaw = this.hasVisualState ? this.renderYaw(0) : tag.getFloat("yaw");
        this.previousPitch = this.hasVisualState ? this.renderPitch(0) : tag.getFloat("pitch");
        this.yaw = tag.getFloat("yaw");
        this.pitch = tag.getFloat("pitch");
        this.loaded = tag.getBoolean("loaded");
        this.reloadStarted = tag.getLong("reloadStarted");
        this.lastShot = tag.getLong("lastShot");
        this.receivedAt = this.level == null ? 0 : this.level.getGameTime();
        this.hasVisualState = true;
        this.status = tag.getInt("status");
        this.clientInterval = tag.getInt("interval");
        this.clientRange = tag.getDouble("range");
        this.clientAngle = tag.getDouble("angle");
        this.clientAmpWarning = tag.getBoolean("ampWarning");
        this.outputBlocked = tag.getBoolean("outputBlocked");
    }

    public CompoundTag portableData() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, Objects.requireNonNull(this.level, "Turret must be attached to a level").registryAccess());
        return tag;
    }

    public void restore(CompoundTag tag) {
        this.loadAdditional(tag, Objects.requireNonNull(this.level, "Turret must be attached to a level").registryAccess());
        this.identity = UUID.randomUUID();
        this.burstTicks = 0;
        this.dirty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", this.inventory.serializeNBT(registries));
        tag.put("oil", this.oil.writeToNBT(registries, new CompoundTag()));
        tag.put("lava", this.lava.writeToNBT(registries, new CompoundTag()));
        tag.putIntArray("faces", this.faces);
        tag.putIntArray("limits", this.limits);
        tag.putInt("priority", this.priority);
        tag.putInt("redstone", this.redstone);
        tag.putBoolean("lock", this.lockTarget);
        tag.putBoolean("stopAmp", this.stopWithoutAmp);
        tag.putBoolean("usedAmp", this.usedAmp);
        tag.putInt("fuelShots", this.fuelShots);
        tag.putInt("ampShots", this.ampShots);
        tag.putInt("ampType", this.ampType);
        tag.putLong("reloadStarted", this.reloadStarted);
        tag.putLong("lastShot", this.lastShot);
        tag.putFloat("yaw", this.yaw);
        tag.putFloat("pitch", this.pitch);
        tag.putUUID("identity", this.identity);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Old turrets stored a nine-slot handler; preserve contents but expand its serialized size.
        CompoundTag inv = tag.getCompound("inventory").copy();
        inv.putInt("Size", 14);
        this.inventory.deserializeNBT(registries, inv);
        this.oil.readFromNBT(registries, tag.getCompound("oil"));
        this.lava.readFromNBT(registries, tag.getCompound("lava"));
        int[] f = tag.getIntArray("faces"), l = tag.getIntArray("limits");
        for (int i = 0; i < Math.min(6, f.length); i++) {
            this.faces[i] = Mth.clamp(f[i], 0, 3);
        }

        for (int i = 0; i < Math.min(5, l.length); i++) {
            this.limits[i] = Math.max(0, l[i]);
        }

        this.priority = tag.contains("priority") ? Mth.clamp(tag.getInt("priority"), 0, 3) : 1;
        this.redstone = Mth.clamp(tag.getInt("redstone"), 0, 2);
        this.lockTarget = !tag.contains("lock") || tag.getBoolean("lock");
        this.stopWithoutAmp = tag.getBoolean("stopAmp");
        this.usedAmp = tag.getBoolean("usedAmp");
        this.fuelShots = Math.clamp(tag.getInt("fuelShots"), 0, 72);
        this.ampShots = Math.clamp(tag.getInt("ampShots"), 0, 16);
        this.ampType = Math.clamp(tag.getInt("ampType"), 0, 2);
        this.reloadStarted = tag.getLong("reloadStarted");
        this.lastShot = tag.getLong("lastShot");
        this.yaw = tag.getFloat("yaw");
        this.pitch = tag.getFloat("pitch");
        if (tag.hasUUID("identity")) {
            this.identity = tag.getUUID("identity");
        }
    }
}
