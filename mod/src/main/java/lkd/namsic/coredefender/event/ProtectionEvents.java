package lkd.namsic.coredefender.event;

import lkd.namsic.coredefender.Balance;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.killzone.KillzoneManager;
import lkd.namsic.coredefender.registry.ModBlocks;
import lkd.namsic.coredefender.wall.DamageNumbers;
import lkd.namsic.coredefender.wall.WallGeometry;
import lkd.namsic.coredefender.wall.WallManager;
import lkd.namsic.coredefender.wall.WallState;
import lombok.experimental.UtilityClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 디펜스 차원 건설 규칙 (크리에이티브 제외):
 * <ul>
 *     <li>성벽 안, 성벽 위(윗면 이상): 자유</li>
 *     <li>성벽 밖: 금지. 단 킬존 장애물은 성벽 바깥면에서 {@link Balance#KILLZONE_WIDTH} 이내에 설치/회수 가능</li>
 * </ul>
 * 성벽/코어 블록은 블록 자체가 파괴 불가.
 */
@EventBusSubscriber(modid = CoreDefender.MOD_ID)
@UtilityClass
public class ProtectionEvents {
    private static WallGeometry geometry(LevelAccessor level) {
        if (!(level instanceof ServerLevel serverLevel) || !DefenseDimension.is(serverLevel)) {
            return null;
        }
        WallState state = WallManager.state(serverLevel);
        return state.built() ? state.geometry() : null;
    }

    private static boolean bypass(Entity entity) {
        return entity instanceof Player player && player.isCreative();
    }

    private static boolean isKillzoneBlock(BlockState state) {
        return state.is(ModBlocks.BARRICADE.get()) || state.is(ModBlocks.SPIKE_TRAP.get());
    }

    /** 일반 블록을 설치/파괴할 수 있는 위치: 성벽 안 또는 성벽 윗면 위 */
    private static boolean isFreeBuildArea(WallGeometry geo, BlockPos pos) {
        return geo.isInterior(pos.getX(), pos.getZ())
                || (geo.isWallColumn(pos.getX(), pos.getZ()) && pos.getY() >= geo.topY());
    }

    private static boolean isKillzone(WallGeometry geo, BlockPos pos) {
        return !geo.isWallColumn(pos.getX(), pos.getZ())
                && geo.distanceToWall(pos.getX() + 0.5, pos.getZ() + 0.5) <= Balance.KILLZONE_WIDTH;
    }

    private static boolean allowed(WallGeometry geo, BlockPos pos, BlockState state) {
        return isFreeBuildArea(geo, pos) || (isKillzoneBlock(state) && isKillzone(geo, pos));
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        WallGeometry geo = geometry(event.getLevel());
        if (geo != null && !bypass(event.getEntity()) && !allowed(geo, event.getPos(), event.getPlacedBlock())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        WallGeometry geo = geometry(event.getLevel());
        if (geo != null && !bypass(event.getPlayer()) && !allowed(geo, event.getPos(), event.getState())) {
            event.setCanceled(true);
        }
    }

    /** 양동이로 유체를 놓는 것은 블록 설치 이벤트가 발생하지 않으므로 별도 차단 */
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
     * 블록 자체는 성 안의 일반 블록만 파괴되고, 성벽 밖 지형/성벽/코어/바리케이드는 남는다.
     */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        WallGeometry geo = geometry(event.getLevel());
        if (geo == null || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        damageWallByExplosion(level, geo, event.getExplosion());
        for (BlockPos pos : event.getAffectedBlocks()) {
            KillzoneManager.damage(level, pos, Balance.EXPLOSION_BARRICADE_DAMAGE);
        }
        event.getAffectedBlocks().removeIf(pos -> !isFreeBuildArea(geo, pos)
                || level.getBlockState(pos).is(ModBlocks.CORE.get())
                || KillzoneManager.isBarricade(level, pos));
    }

    /**
     * 성벽 블록은 폭발 저항이 무한이라 바닐라 폭발 대상 목록에 들어가지 않으므로,
     * 폭발 중심에서 가까운 성벽 블록 수와 거리로 구간별 피해를 직접 계산한다.
     */
    private static void damageWallByExplosion(ServerLevel level, WallGeometry geo, Explosion explosion) {
        Vec3 center = explosion.center();
        double range = explosion.radius() * 1.5;
        int r = (int) Math.ceil(range);
        Map<String, Double> damage = new LinkedHashMap<>();
        BlockPos origin = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            if (!geo.isWallBlock(pos)) {
                continue;
            }
            double distance = Vec3.atCenterOf(pos).distanceTo(center);
            if (distance < range) {
                damage.merge(geo.segmentAt(pos.getX(), pos.getZ()), Balance.EXPLOSION_WALL_DAMAGE * (1 - distance / range), Double::sum);
            }
        }
        damage.forEach((segment, amount) -> {
            int rounded = (int) Math.round(amount);
            if (rounded <= 0) {
                return;
            }
            WallManager.damage(level, segment, rounded);
            DamageNumbers.spawn(level, center, rounded, ChatFormatting.GOLD);
        });
    }
}
