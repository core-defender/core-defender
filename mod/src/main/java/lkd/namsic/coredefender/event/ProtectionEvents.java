package lkd.namsic.coredefender.event;

import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.DefenseDimension;
import lkd.namsic.coredefender.registry.ModBlocks;
import lkd.namsic.coredefender.wall.WallGeometry;
import lkd.namsic.coredefender.wall.WallManager;
import lkd.namsic.coredefender.wall.WallState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * 디펜스 차원 건설 규칙: 성벽 안은 자유, 성벽 밖은 금지 (크리에이티브 제외).
 * 성벽/코어 블록은 블록 자체가 파괴 불가.
 */
@EventBusSubscriber(modid = CoreDefender.MOD_ID)
public final class ProtectionEvents {
    private ProtectionEvents() {
    }

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

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        WallGeometry geo = geometry(event.getLevel());
        if (geo == null || bypass(event.getEntity())) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!geo.isInterior(pos.getX(), pos.getZ())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        WallGeometry geo = geometry(event.getLevel());
        if (geo == null || bypass(event.getPlayer())) {
            return;
        }
        BlockPos pos = event.getPos();
        if (!geo.isInterior(pos.getX(), pos.getZ())) {
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
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos target = blockHit.getBlockPos().relative(blockHit.getDirection());
            if (geo.isInterior(target.getX(), target.getZ())) {
                return;
            }
        }
        event.setCanceled(true);
    }

    /** 폭발: 성벽 밖 지형과 성벽/코어 블록은 파괴되지 않는다 (엔티티 피해는 그대로) */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        WallGeometry geo = geometry(event.getLevel());
        if (geo == null) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> !geo.isInterior(pos.getX(), pos.getZ())
                || event.getLevel().getBlockState(pos).is(ModBlocks.CORE.get()));
    }
}
