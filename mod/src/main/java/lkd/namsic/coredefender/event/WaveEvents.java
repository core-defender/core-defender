package lkd.namsic.coredefender.event;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.killzone.KillzoneManager;
import lkd.namsic.coredefender.turret.BasicTurretBlockEntity;
import lkd.namsic.coredefender.wall.DamageNumbers;
import lkd.namsic.coredefender.wave.WaveManager;
import lkd.namsic.coredefender.wave.WaveMobController;
import lkd.namsic.coredefender.wave.WaveMobs;
import lkd.namsic.coredefender.wave.WaveProjectiles;
import lkd.namsic.coredefender.wave.SummonerWard;
import net.minecraft.tags.DamageTypeTags;
import lkd.namsic.coredefender.wave.TestMobs;
import lombok.experimental.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = CoreDefender.MOD_ID)
@UtilityClass
public class WaveEvents {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        WaveManager.tick(event.getServer());
        TestMobs.tick(event.getServer());
        DamageNumbers.tick();
    }

    /**
     * 진행 중인 웨이브가 추적하지 않는 웨이브 몹(재시작 등으로 남은 것)은 월드에 들이지 않는다.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Projectile projectile) {
            WaveProjectiles.rememberOwner(projectile);
        }

        // 저장됐다가 다시 로드된 피해 숫자는 버린다
        if (event.loadedFromDisk() && DamageNumbers.isDamageNumber(event.getEntity())) {
            event.setCanceled(true);
            return;
        }

        if (!WaveMobs.isWaveMob(event.getEntity())) {
            return;
        }

        WaveManager.ActiveWave wave = WaveManager.getActive();
        if ((wave == null || !wave.tracks(event.getEntity().getUUID())) && !TestMobs.tracks(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * 플레이어가 웨이브 몹을 공격 → 주변 몹 일부가 플레이어를 노린다.
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        WaveManager.ActiveWave wave = WaveManager.getActive();
        // Minecraft owns this level; borrowers must not close it.
        //noinspection resource
        if (!(event.getEntity() instanceof Mob mob) || !WaveMobs.isWaveMob(mob)
            || !(event.getSource().getEntity() instanceof Player player) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }

        WaveMobController.onPlayerAttack(level, player, mob, wave);
    }

    /**
     * 웨이브 몹끼리는 서로 피해를 주지 않는다 (오사격/폭발로 인한 내분 방지)
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (WaveMobs.isWaveMob(event.getEntity()) && (WaveMobs.isWaveMob(event.getSource().getEntity())
            || event.getSource().getDirectEntity() instanceof Projectile projectile && WaveProjectiles.isHostile(projectile))) {
            event.setCanceled(true);
            return;
        }

        if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setAmount(event.getAmount() * (float) (1 - SummonerWard.reduction(event.getEntity())));
        }
    }

    /**
     * 웨이브 몹이 쏜 투사체: 다른 웨이브 몹은 통과하고, 성벽/바리케이드에 맞으면 피해를 준다.
     * 포탑 화살: 플레이어를 통과한다.
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.getTags().contains(BasicTurretBlockEntity.ARROW_TAG)
            && event.getRayTraceResult() instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player) {
            // 포탑 화살은 플레이어를 통과
            event.setCanceled(true);
            return;
        }

        if (!WaveProjectiles.isHostile(projectile)) {
            return;
        }

        HitResult result = event.getRayTraceResult();
        if (result instanceof EntityHitResult entityHit) {
            if (WaveMobs.isWaveMob(entityHit.getEntity())) {
                event.setCanceled(true);
            }

            return;
        }

        // Minecraft owns this level; borrowers must not close it.
        //noinspection resource
        if (!(projectile.level() instanceof ServerLevel level) || !DefenseDimension.is(level)
            || !(result instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = hit.getBlockPos();
        boolean damaged = KillzoneManager.damage(level, pos, (int) Math.round(Balance.RANGED_WALL_DAMAGE * WaveProjectiles.damageScale(projectile)));
        if (damaged) {
            projectile.discard();
            event.setCanceled(true);
        }
    }
}
