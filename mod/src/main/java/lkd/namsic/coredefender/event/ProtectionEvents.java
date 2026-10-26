package lkd.namsic.coredefender.event;

import org.jspecify.annotations.Nullable;

import lkd.namsic.coredefender.wall.BarrierGeometry;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.wave.WaveMobs;
import lkd.namsic.coredefender.wall.BarrierSystem;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.killzone.KillzoneManager;
import lkd.namsic.coredefender.registry.ModBlocks;
import lkd.namsic.coredefender.wall.WallGeometry;
import lkd.namsic.coredefender.wall.WallManager;
import lkd.namsic.coredefender.wall.WallState;
import lombok.experimental.UtilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;


/**
 * 디펜스 차원 건설 규칙 (크리에이티브 제외):
 * <ul>
 *     <li>장벽 내부: 자유</li>
 *     <li>성벽 밖: 금지. 단 킬존 장애물은 성벽 바깥면에서 {@link Balance#KILLZONE_WIDTH} 이내에 설치/회수 가능</li>
 * </ul>
 * 코어 블록은 파괴 불가. 장벽에는 실체 블록이 없다.
 */
@EventBusSubscriber(modid = CoreDefender.MOD_ID)
@UtilityClass
public class ProtectionEvents {
    private static @Nullable WallGeometry geometry(LevelAccessor level) {
        if (!(level instanceof ServerLevel serverLevel) || !DefenseDimension.is(serverLevel)) {
            return null;
        }

        WallState state = WallManager.state(serverLevel);
        return state.built() ? state.geometry() : null;
    }

    private static boolean bypass(@Nullable Entity entity) {
        return entity instanceof Player player && player.isCreative();
    }

    private static boolean isKillzoneBlock(BlockState state) {
        return state.is(ModBlocks.BARRICADE.get()) || state.is(ModBlocks.SPIKE_TRAP.get());
    }

    /**
     * 일반 블록을 설치/파괴할 수 있는 위치: 장벽 내부
     */
    private static boolean isFreeBuildArea(WallGeometry geo, BlockPos pos) {
        return geo.isInterior(pos.getX(), pos.getZ());
    }

    private static boolean isKillzone(WallGeometry geo, BlockPos pos) {
        return !geo.isWallColumn(pos.getX(), pos.getZ())
            && geo.distanceToWall(pos.getX() + 0.5, pos.getZ() + 0.5) <= Balance.KILLZONE_WIDTH;
    }

    private static boolean disallowed(WallGeometry geo, BlockPos pos, BlockState state) {
        return !isFreeBuildArea(geo, pos) && !(isKillzoneBlock(state) && isKillzone(geo, pos));
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        WallGeometry geo = geometry(event.getLevel());
        if (geo != null && !bypass(event.getEntity()) && disallowed(geo, event.getPos(), event.getPlacedBlock())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        WallGeometry geo = geometry(event.getLevel());
        if (geo != null && !bypass(event.getPlayer()) && disallowed(geo, event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    /**
     * 양동이로 유체를 놓는 것은 블록 설치 이벤트가 발생하지 않으므로 별도 차단
     */
    @SubscribeEvent
    public static void onUseBucket(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        WallGeometry geo = geometry(event.getLevel());
        if (geo == null || bypass(player)) {
            return;
        }

        if (!(event.getItemStack().getItem() instanceof BucketItem bucket) || bucket.content == Fluids.EMPTY) {
            return;
        }

        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
            && isFreeBuildArea(geo, blockHit.getBlockPos().relative(blockHit.getDirection()))) {
            return;
        }

        event.setCanceled(true);
    }

    /**
     * 폭발: 근처 성벽 구간과 휘말린 바리케이드는 HP 피해를 받는다.
     * 웨이브 적과 크리퍼 폭발은 성 안 블록을 파괴하지 않는다. 성벽 밖 지형/코어/바리케이드도 보호한다.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        WallGeometry geo = geometry(event.getLevel());
        if (geo == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        protectExplosion(level, geo, event);
    }

    static void protectExplosion(ServerLevel level, WallGeometry geo, ExplosionEvent.Detonate event) {
        boolean hostile = WaveMobs.isWaveMob(event.getExplosion().getDirectSourceEntity())
            || WaveMobs.isWaveMob(event.getExplosion().getIndirectSourceEntity())
            || event.getExplosion().getDirectSourceEntity() instanceof Creeper
            || event.getExplosion().getIndirectSourceEntity() instanceof Creeper;
        if (hostile) {
            WallState wall = WallManager.state(level);
            Vec3 center = event.getExplosion().center();
            // 이번 폭발로 구간이 붕괴하더라도 해당 폭발은 장벽이 먼저 흡수한다.
            event.getAffectedEntities().removeIf(entity -> WaveMobs.isWaveMob(entity)
                || BarrierSystem.trace(wall, center, entity.getBoundingBox().getCenter()) != null);
            // Floor/roof rays can miss the finite-height barrier; interior blocks remain protected.
            event.getAffectedBlocks().removeIf(pos -> geo.isInterior(pos.getX(), pos.getZ())
                || BarrierSystem.trace(wall, center, Vec3.atCenterOf(pos)) != null);
            damageWallByExplosion(level, event.getExplosion());
        }

        for (BlockPos pos : event.getAffectedBlocks()) {
            KillzoneManager.damage(level, pos, Balance.EXPLOSION_BARRICADE_DAMAGE);
        }

        event.getAffectedBlocks().removeIf(pos -> !isFreeBuildArea(geo, pos)
            || level.getBlockState(pos).is(ModBlocks.CORE.get())
            || KillzoneManager.isBarricade(level, pos));
    }

    /**
     * 적 폭발의 반경과 각 장벽 면까지의 거리로 구간별 피해를 계산한다.
     */
    private static void damageWallByExplosion(ServerLevel level, Explosion explosion) {
        Vec3 center = explosion.center();
        double range = explosion.radius() * 1.5;
        if (range <= 0) {
            return;
        }

        WallState state = WallManager.state(level);
        for (BarrierGeometry.Panel panel : state.panels()) {
            Vec3 point = panel.nearest(center);
            double distance = point.distanceTo(center);
            if (!state.active(panel.id()) || distance >= range) {
                continue;
            }

            double amount = Balance.EXPLOSION_WALL_DAMAGE * range * 4 * (1 - distance / range)
                * WaveMobs.damageScale(explosion.getIndirectSourceEntity());
            WallManager.hit(level, panel.id(), point, amount);
        }
    }
}
