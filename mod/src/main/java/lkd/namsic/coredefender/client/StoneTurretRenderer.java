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
                              MultiBufferSource buffers, int light, int overlay, float draw, float shotAge, float recoil) {
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
        // Preserve the original arm pivot and reload curve; local +Z is the firing direction.
        pose.mulPose(Axis.XP.rotationDegrees(45 - draw * 45));
        renderCentered("arm", pose, buffers, light, overlay);
        if (turret.isLoaded() && (shotAge < 0 || shotAge >= 4)) {
            renderCentered("ammo", pose, buffers, light, overlay);
        }

        pose.popPose();
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
