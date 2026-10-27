package lkd.namsic.coredefender.client;

import com.mojang.blaze3d.vertex.PoseStack;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.registry.ModBlockEntities;
import lkd.namsic.coredefender.turret.BasicTurretBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 포탑 블록 엔티티 렌더러. 장전/발사 진행도를 계산하고 포탑 종류별 전용 모델 렌더러에 위임한다.
 */
@EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
public final class BasicTurretRenderer implements BlockEntityRenderer<BasicTurretBlockEntity> {
    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.BASIC_TURRET.get(), context -> new BasicTurretRenderer());
    }

    @Override
    public AABB getRenderBoundingBox(BasicTurretBlockEntity turret) {
        return new AABB(turret.getBlockPos()).inflate(1);
    }

    @Override
    public void render(BasicTurretBlockEntity turret, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (turret.getLevel() == null) {
            return;
        }

        double now = turret.getLevel().getGameTime() + partialTick;
        float reload = (float) Math.clamp((now - turret.getReloadStarted()) / Math.max(1, turret.clientInterval), 0, 1);
        float draw = turret.isLoaded() ? reload * reload * (3 - 2 * reload) : 0;
        float shotAge = (float) (now - turret.getLastShot());
        float recoil = shotAge >= 0 && shotAge < 5 ? .10F * (1 - shotAge / 5) : 0;

        switch (turret.kind()) {
            case 1 -> StoneTurretRenderer.render(turret, partialTick, pose, buffers, light, overlay, draw, shotAge, recoil);
            case 3 -> OilTurretRenderer.render(turret, partialTick, shotAge, recoil, pose, buffers, light, overlay);
            // 0 = 화살, 2 = 화염
            default -> ArrowFlameTurretModels.render(turret, partialTick, pose, buffers, light, overlay, reload, draw, shotAge, recoil);
        }
    }
}
