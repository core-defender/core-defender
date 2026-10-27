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
public final class OilTurretRenderer {
    private static final ModelResourceLocation YOKE = model("yoke");
    private static final ModelResourceLocation POT = model("pot");
    private static final ModelResourceLocation OIL = model("oil");
    private static final ModelResourceLocation RED = model("lamp_red");
    private static final ModelResourceLocation AMBER = model("lamp_amber");
    private static final ModelResourceLocation GREEN = model("lamp_green");
    private static final ModelResourceLocation GRAY = model("lamp_gray");

    private OilTurretRenderer() {
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (ModelResourceLocation id : new ModelResourceLocation[]{YOKE, POT, OIL, RED, AMBER, GREEN, GRAY}) {
            event.register(id);
        }
    }

    public static void render(BasicTurretBlockEntity turret, float partialTick, float shotAge, float recoil,
                              PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        boolean warning = turret.status == 2 || turret.status == 3 || turret.status == 4 || turret.status == 8
            || turret.status == 9 || turret.status == 11;
        ModelResourceLocation lamp = warning ? RED : turret.clientAmpWarning || turret.outputBlocked ? AMBER
            : turret.status == 7 ? GRAY : GREEN;
        renderPart(lamp, turret, pose, buffers, 15728880, overlay);
        pose.pushPose();
        // Keep the shared renderer's original yaw, pitch, recoil and pouring pivot.
        pose.translate(.5, 1.3, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-turret.renderYaw(partialTick)));
        renderPart(YOKE, turret, pose, buffers, light, overlay);
        pose.mulPose(Axis.XP.rotationDegrees(turret.renderPitch(partialTick)));
        pose.translate(0, 0, -recoil);
        float tilt = shotAge >= 0 && shotAge < 20 ? (float) Math.sin(shotAge / 20 * Math.PI) * 65 : 0;
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        renderPart(POT, turret, pose, buffers, light, overlay);
        if (turret.isLoaded() || shotAge >= 0 && shotAge < 20) {
            renderPart(OIL, turret, pose, buffers, light, overlay);
        }

        pose.popPose();
    }

    private static ModelResourceLocation model(String part) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "block/oil_turret_" + part));
    }

    private static void renderPart(ModelResourceLocation id, BasicTurretBlockEntity turret, PoseStack pose,
                                   MultiBufferSource buffers, int light, int overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel baked = minecraft.getModelManager().getModel(id);
        minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.solid()),
            turret.getBlockState(), baked, 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.solid());
    }
}
