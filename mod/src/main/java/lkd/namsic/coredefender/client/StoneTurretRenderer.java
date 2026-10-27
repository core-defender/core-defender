package lkd.namsic.coredefender.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.turret.BasicTurretBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
public final class StoneTurretRenderer {
    private static final float RELEASED = 45;
    private static final float OVERSHOOT = 52;
    private static final float THROW_TICKS = 3;
    private static final float SETTLE_TICKS = 4;

    private StoneTurretRenderer() {
    }

    private static ModelResourceLocation model(String part) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "block/stone_turret_" + part));
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (String part : new String[]{"bearing", "head", "arm", "ammo", "lamp_ready", "lamp_warning", "lamp_amp", "lamp_stopped"}) {
            event.register(model(part));
        }
    }

    public static void render(BasicTurretBlockEntity turret, float partialTick, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay, float reload, float shotAge, float recoil) {
        boolean warning = turret.status == 2 || turret.status == 3 || turret.status == 4 || turret.status == 8 || turret.status == 9 || turret.status == 11;
        String lamp = warning ? "warning" : turret.clientAmpWarning || turret.outputBlocked ? "amp" : turret.status == 7 ? "stopped" : "ready";
        renderPart("lamp_" + lamp, pose, buffers, 15728880, overlay);
        pose.pushPose();
        pose.translate(.5, 1.3, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-turret.renderYaw(partialTick)));
        renderCentered("bearing", pose, buffers, light, overlay);
        pose.mulPose(Axis.XP.rotationDegrees(turret.renderPitch(partialTick)));
        pose.translate(0, 0, -recoil);
        renderCentered("head", pose, buffers, light, overlay);
        float wind = windProgress(turret, reload, shotAge);
        // local +Z 가 발사 방향이다. 0° 는 장전 자세, 45° 는 던진 뒤 자세.
        pose.mulPose(Axis.XP.rotationDegrees(armAngle(turret, wind, shotAge)));
        renderCentered("arm", pose, buffers, light, overlay);
        if (turret.isLoaded() && wind >= .85F) {
            renderCentered("ammo", pose, buffers, light, overlay);
        }

        pose.popPose();
    }

    /**
     * 던진 뒤 팔이 멈추는 시간을 빼고, 재장전 시간의 90% 안에 팔을 다시 감아 올린다.
     */
    private static float windProgress(BasicTurretBlockEntity turret, float reload, float shotAge) {
        if (!turret.isLoaded()) {
            return 0;
        }

        if (shotAge >= 0 && shotAge < THROW_TICKS + SETTLE_TICKS) {
            return 0;
        }

        float ticks = reload * turret.clientInterval;
        float span = Math.max(1, turret.clientInterval * .9F - THROW_TICKS - SETTLE_TICKS);
        float t = Math.clamp((ticks - THROW_TICKS - SETTLE_TICKS) / span, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float armAngle(BasicTurretBlockEntity turret, float wind, float shotAge) {
        if (!turret.isLoaded()) {
            return RELEASED;
        }

        if (shotAge >= 0 && shotAge < THROW_TICKS) {
            float t = shotAge / THROW_TICKS;
            return OVERSHOOT * (1 - (1 - t) * (1 - t));
        }

        if (shotAge >= THROW_TICKS && shotAge < THROW_TICKS + SETTLE_TICKS) {
            float t = (shotAge - THROW_TICKS) / SETTLE_TICKS;
            return OVERSHOOT - (OVERSHOOT - RELEASED) * t * t * (3 - 2 * t);
        }

        return RELEASED * (1 - wind);
    }

    private static void renderCentered(String part, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(-.5, -.5, -.5);
        renderPart(part, pose, buffers, light, overlay);
        pose.popPose();
    }

    private static void renderPart(String part, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel baked = minecraft.getModelManager().getModel(model(part));
        minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.solid()),
            null, baked, 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.solid());
    }
}
