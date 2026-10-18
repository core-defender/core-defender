package lkd.namsic.coredefender.turret;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.registry.ModBlockEntities;
import lkd.namsic.coredefender.registry.ModBlocks;
import lkd.namsic.coredefender.wave.WaveMobs;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * 기초 포탑: 동력 없이 화살을 소모해 사거리 안의 가장 가까운 웨이브 몹을 쏜다.
 * 화살은 우클릭 또는 호퍼/파이프로 넣는다.
 */
public class BasicTurretBlockEntity extends BlockEntity {
    public static final String ARROW_TAG = "cd_turret_arrow";

    @Getter
    private final ItemStackHandler inventory = new ItemStackHandler(9) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(Items.ARROW);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int cooldown;

    public BasicTurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BASIC_TURRET.get(), pos, state);
    }

    public int arrowCount() {
        int count = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            count += inventory.getStackInSlot(i).getCount();
        }
        return count;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BasicTurretBlockEntity turret) {
        if (--turret.cooldown > 0 || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        turret.cooldown = Balance.TURRET_INTERVAL;

        Vec3 muzzle = Vec3.atCenterOf(pos).add(0, 0.8, 0);
        Mob target = null;
        Vec3 launch = null;
        double best = Double.MAX_VALUE;
        for (Mob mob : serverLevel.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(Balance.TURRET_RANGE), WaveMobs::isWaveMob)) {
            double distance = mob.distanceToSqr(muzzle);
            if (!mob.isAlive() || distance > Balance.TURRET_RANGE * Balance.TURRET_RANGE || distance >= best) {
                continue;
            }
            Vec3 point = launchPoint(serverLevel, muzzle, mob);
            if (point != null) {
                target = mob;
                launch = point;
                best = distance;
            }
        }
        if (target == null || !turret.consumeArrow()) {
            return;
        }

        Arrow arrow = new Arrow(serverLevel, launch.x, launch.y, launch.z, new ItemStack(Items.ARROW), null);
        Vec3 aim = target.getBoundingBox().getCenter().subtract(launch);
        arrow.shoot(aim.x, aim.y + aim.horizontalDistance() * 0.1, aim.z, 2.4F, 1.0F);
        arrow.setBaseDamage(Balance.TURRET_ARROW_DAMAGE);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.addTag(ARROW_TAG);
        serverLevel.addFreshEntity(arrow);
        serverLevel.playSound(null, pos, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 0.6F, 1.2F);
    }

    /**
     * 화살 발사 지점. 목표까지의 선이 성벽 블록을 지나면 성벽을 빠져나온 지점에서 쏜다
     * (성벽 위 포탑이 총안으로 쏘듯 벽에 붙은 몹도 맞힐 수 있다). 성벽이 아닌 블록에 막히면 null.
     */
    @Nullable
    private static Vec3 launchPoint(ServerLevel level, Vec3 muzzle, Mob mob) {
        Vec3 target = mob.getBoundingBox().getCenter();
        Vec3 start = muzzle;
        for (int i = 0; i < 8; i++) {
            BlockHitResult hit = level.clip(new ClipContext(start, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            if (hit.getType() == HitResult.Type.MISS) {
                return start;
            }
            if (!level.getBlockState(hit.getBlockPos()).is(ModBlocks.WALL.get())) {
                return null;
            }
            // 성벽 블록을 빠져나올 때까지 선을 따라 전진
            Vec3 direction = target.subtract(hit.getLocation()).normalize().scale(0.1);
            Vec3 point = hit.getLocation();
            while (point.distanceToSqr(target) > 0.25 && level.getBlockState(BlockPos.containing(point)).is(ModBlocks.WALL.get())) {
                point = point.add(direction);
            }
            start = point.add(direction.scale(3));
        }
        return null;
    }

    private boolean consumeArrow() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.extractItem(i, 1, false).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("inventory"));
    }
}
