package lkd.namsic.coredefender.wave;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.killzone.KillzoneManager;
import lkd.namsic.coredefender.wall.WallGeometry;
import lkd.namsic.coredefender.wall.WallManager;
import lombok.experimental.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 웨이브 몹 행동. 타겟(어그로한 플레이어)이 없으면 성벽을 향해 이동/공격한다.
 * 타겟이 있으면 바닐라 공격 AI 에 맡기고, 어그로 만료/이탈/도달 불가 시 타겟을 해제한다.
 */
@UtilityClass
public class WaveMobController {
    /** 몹 하나의 런타임 상태 (저장하지 않음) */
    public static final class MobState {
        long nextAttack;
        long aggroUntil;
        long nextReachCheck;
        @Nullable
        BlockPos lastWaypoint;
        /** 성벽에 접근할 때 몹마다 다른 지점으로 흩어지기 위한 오프셋 */
        final double spreadX;
        final double spreadZ;

        public MobState(RandomSource random) {
            this.spreadX = (random.nextDouble() * 2 - 1) * SPREAD;
            this.spreadZ = (random.nextDouble() * 2 - 1) * SPREAD;
        }
    }

    private static final double MELEE_REACH = 2.2;
    /** 성벽 바깥면에서 이 거리 안이면 성벽을 공격 (앞줄 뒤의 몹도 때릴 수 있도록 여유) */
    private static final double WALL_ATTACK_REACH = 2.0;
    /** 이 거리 안에서는 플로우 필드 대신 각자의 공격 지점으로 직접 접근 */
    private static final double APPROACH_RANGE = 8.0;
    private static final double SPREAD = 4.0;
    private static final int LOOKAHEAD = 6;
    private static final int REACH_CHECK_INTERVAL = 40;

    public static void tick(ServerLevel level, Mob mob, MobState state, WallGeometry geo, FlowField field) {
        long now = level.getGameTime();
        if (handleAggro(level, mob, state, now)) {
            return;
        }

        if (WaveMobs.isBreaker(mob) && attackNearbyBarricade(level, mob, state, now)) {
            return;
        }

        double distance = geo.distanceToWall(mob.getX(), mob.getZ());
        BlockPos wallBlock = geo.nearestWallBlock(mob.getX(), mob.getEyeY(), mob.getZ());

        if (mob instanceof RangedAttackMob && distance <= Balance.RANGED_WALL_RANGE) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(Vec3.atCenterOf(wallBlock));
            if (now >= state.nextAttack) {
                shootAtWall(level, mob, wallBlock);
                state.nextAttack = now + Balance.RANGED_INTERVAL;
            }
            return;
        }

        if (distance <= WALL_ATTACK_REACH) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(Vec3.atCenterOf(wallBlock));
            if (mob instanceof Creeper creeper) {
                creeper.ignite();
            } else if (now >= state.nextAttack) {
                mob.swing(InteractionHand.MAIN_HAND);
                WallManager.damageAt(level, wallBlock, Math.max(1, Math.round(WaveMobs.attackDamage(mob))));
                state.nextAttack = now + Balance.MELEE_INTERVAL;
            }
            return;
        }

        if (distance <= APPROACH_RANGE) {
            moveTo(mob, state, attackSpot(geo, mob, state));
            return;
        }

        FlowField.Step step = field.next(mob.getBlockX(), mob.getBlockZ(), LOOKAHEAD);
        if (step == null) {
            moveTo(mob, state, wallBlock);
            return;
        }
        if (step.barricade() != null && mob.position().distanceTo(Vec3.atCenterOf(step.barricade())) <= MELEE_REACH) {
            attackBarricade(level, mob, state, step.barricade(), now);
            return;
        }
        moveTo(mob, state, step.waypoint());
    }

    /** 성벽 바로 바깥 한 칸 둘레 위에서, 몹 위치 + 개별 오프셋에 가장 가까운 지점 */
    private static BlockPos attackSpot(WallGeometry geo, Mob mob, MobState state) {
        int x0 = geo.outerMinX() - 1;
        int x1 = geo.outerMaxX() + 1;
        int z0 = geo.outerMinZ() - 1;
        int z1 = geo.outerMaxZ() + 1;
        int x = (int) Math.floor(Math.clamp(mob.getX() + state.spreadX, x0, x1));
        int z = (int) Math.floor(Math.clamp(mob.getZ() + state.spreadZ, z0, z1));
        if (x > x0 && x < x1 && z > z0 && z < z1) {
            // 오프셋 때문에 성벽 안쪽을 가리키면 가장 가까운 바깥 둘레로 밀어낸다
            int toWest = x - x0;
            int toEast = x1 - x;
            int toNorth = z - z0;
            int toSouth = z1 - z;
            int min = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));
            if (min == toWest) {
                x = x0;
            } else if (min == toEast) {
                x = x1;
            } else if (min == toNorth) {
                z = z0;
            } else {
                z = z1;
            }
        }
        return new BlockPos(x, geo.baseY(), z);
    }

    private static void moveTo(Mob mob, MobState state, BlockPos target) {
        if (!target.equals(state.lastWaypoint) || mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            state.lastWaypoint = target;
        }
    }

    // ---------- 플레이어 어그로 ----------

    /** 플레이어 타겟을 유지하면 true. 만료/이탈/도달 불가면 타겟을 해제하고 false. */
    private static boolean handleAggro(ServerLevel level, Mob mob, MobState state, long now) {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return false;
        }
        boolean keep = target.isAlive()
                && target instanceof Player player && !player.isCreative() && !player.isSpectator()
                && now < state.aggroUntil
                && mob.distanceTo(target) <= Balance.AGGRO_LEASH;
        if (keep && !(mob instanceof RangedAttackMob) && now >= state.nextReachCheck) {
            state.nextReachCheck = now + REACH_CHECK_INTERVAL;
            keep = canReach(mob, target);
        }
        if (!keep) {
            mob.setTarget(null);
            state.lastWaypoint = null;
        }
        return keep;
    }

    /** 플레이어가 웨이브 몹을 공격했을 때: 주변에서 플레이어에게 닿을 수 있는 몹만 반응한다. */
    public static void onPlayerAttack(ServerLevel level, Player player, Mob attacked, WaveManager.ActiveWave wave) {
        long now = level.getGameTime();
        for (Mob mob : level.getEntitiesOfClass(Mob.class, attacked.getBoundingBox().inflate(Balance.AGGRO_RADIUS), WaveMobs::isWaveMob)) {
            MobState state = wave.mobState(mob.getUUID());
            if (state == null) {
                continue;
            }
            boolean able = mob instanceof RangedAttackMob
                    ? mob.distanceTo(player) <= Balance.AGGRO_LEASH && mob.hasLineOfSight(player)
                    : canReach(mob, player);
            if (able) {
                mob.setTarget(player);
                state.aggroUntil = now + Balance.AGGRO_DURATION;
                state.nextReachCheck = now + REACH_CHECK_INTERVAL;
            }
        }
    }

    private static boolean canReach(Mob mob, LivingEntity target) {
        Path path = mob.getNavigation().createPath(target, 1);
        return path != null && path.canReach();
    }

    // ---------- 공격 ----------

    private static void shootAtWall(ServerLevel level, Mob mob, BlockPos wallBlock) {
        ItemStack weapon = mob.getMainHandItem();
        AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, new ItemStack(Items.ARROW), 1.0F, weapon.isEmpty() ? null : weapon);
        Vec3 target = Vec3.atCenterOf(wallBlock);
        double dx = target.x - mob.getX();
        double dz = target.z - mob.getZ();
        double dy = target.y - arrow.getY() + Math.sqrt(dx * dx + dz * dz) * 0.2;
        arrow.shoot(dx, dy, dz, 1.6F, 4.0F);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        mob.swing(InteractionHand.MAIN_HAND);
        mob.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(arrow);
    }

    private static void attackBarricade(ServerLevel level, Mob mob, MobState state, BlockPos barricade, long now) {
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(Vec3.atCenterOf(barricade));
        if (now >= state.nextAttack) {
            mob.swing(InteractionHand.MAIN_HAND);
            KillzoneManager.damage(level, barricade, Math.max(1, Math.round(WaveMobs.attackDamage(mob))));
            state.nextAttack = now + Balance.MELEE_INTERVAL;
        }
    }

    /** 파괴자 몹: 근처 바리케이드를 우선 공격 */
    private static boolean attackNearbyBarricade(ServerLevel level, Mob mob, MobState state, long now) {
        BlockPos nearest = null;
        double best = 16 * 16;
        for (long key : KillzoneManager.state(level).hp().keySet()) {
            BlockPos pos = BlockPos.of(key);
            double d = mob.distanceToSqr(Vec3.atCenterOf(pos));
            if (d < best) {
                best = d;
                nearest = pos;
            }
        }
        if (nearest == null) {
            return false;
        }
        if (Math.sqrt(best) <= MELEE_REACH) {
            attackBarricade(level, mob, state, nearest, now);
        } else {
            moveTo(mob, state, nearest);
        }
        return true;
    }
}
