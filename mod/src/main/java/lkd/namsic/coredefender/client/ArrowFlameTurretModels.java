package lkd.namsic.coredefender.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.turret.BasicTurretBlockEntity;
import lkd.namsic.coredefender.turret.TurretPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
public final class ArrowFlameTurretModels {
    private ArrowFlameTurretModels() {
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        for (String part : new String[]{"head", "bearing", "lamp", "bolt", "left", "right", "string"}) {
            event.register(location("arrow_turret_" + part));
        }

        for (String part : new String[]{"head", "bearing", "lamp", "flash"}) {
            event.register(location("flame_turret_" + part));
        }
    }

    private static ModelResourceLocation location(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "block/" + name));
    }

    public static void render(BasicTurretBlockEntity turret, float partialTick, PoseStack pose,
                              MultiBufferSource buffers, int light, int overlay, float reload, float draw,
                              float shotAge, float recoil) {
        String prefix = turret.kind() == 2 ? "flame_turret_" : "arrow_turret_";
        boolean warning = turret.status == 2 || turret.status == 3 || turret.status == 4 || turret.status == 8 || turret.status == 9 || turret.status == 11;
        boolean amber = turret.clientAmpWarning || turret.outputBlocked;
        float red = warning || amber ? 1 : turret.status == 7 ? .35F : .2F;
        float green = warning ? .12F : amber ? .65F : turret.status == 7 ? .35F : 1;
        float blue = turret.status == 7 ? .35F : .12F;
        part(prefix + "lamp", pose, buffers, 15728880, overlay, red, green, blue);
        pose.pushPose();
        // Keep the server muzzle, yaw/pitch axes and recoil in the original coordinate system.
        pose.translate(.5, 1.3, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-turret.renderYaw(partialTick)));
        part(prefix + "bearing", pose, buffers, light, overlay, 1, 1, 1);
        pose.mulPose(Axis.XP.rotationDegrees(turret.renderPitch(partialTick)));
        pose.translate(0, 0, -recoil);
        part(prefix + "head", pose, buffers, light, overlay, 1, 1, 1);
        if (turret.kind() == 2) {
            if (shotAge >= 0 && shotAge < 10) {
                part(prefix + "flash", pose, buffers, 15728880, overlay, 1, 1, 1);
            }
        } else {
            for (int side : new int[]{-1, 1}) {
                pose.pushPose();
                pose.translate(0, 0, .25);
                pose.mulPose(Axis.YP.rotationDegrees(-side * 22.5F * (1 - draw)));
                pose.translate(0, 0, -.25);
                part(prefix + (side < 0 ? "left" : "right"), pose, buffers, light, overlay, 1, 1, 1);
                pose.popPose();
                float angle = (float) Math.toRadians(22.5 * draw);
                float tipX = (float) Math.cos(angle) * 7.75F / 16;
                float tipZ = .25F - (float) Math.sin(angle) * 7.75F / 16;
                string(pose, buffers, side * tipX, tipZ, tipZ - tipX * draw, light, overlay);
            }

            if (turret.isLoaded() && reload >= .2F) {
                float feed = Math.clamp((reload - .2F) / .5F, 0, 1);
                float slide = 1 - feed * feed * (3 - 2 * feed);
                pose.pushPose();
                // 화살은 레일 높이를 유지한 채 뒤쪽에서 밀려 들어와 장전된다.
                pose.translate(0, TurretPose.ARROW_HEIGHT, -.3 * slide);
                part(prefix + "bolt", pose, buffers, light, overlay, 1, 1, 1);
                pose.popPose();
            }
        }

        pose.popPose();
    }

    private static void string(PoseStack pose, MultiBufferSource buffers, float x, float z, float centerZ,
                               int light, int overlay) {
        Vector3f direction = new Vector3f(-x, 0, centerZ - z);
        float length = direction.length();
        pose.pushPose();
        pose.translate(x, .06, z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), direction.normalize()));
        pose.scale(1, 1, length / .75F);
        part("arrow_turret_string", pose, buffers, light, overlay, 1, 1, 1);
        pose.popPose();
    }

    private static void part(String name, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                             float red, float green, float blue) {
        Minecraft client = Minecraft.getInstance();
        client.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()),
            null, client.getModelManager().getModel(location(name)), red, green, blue, light, overlay,
            ModelData.EMPTY, RenderType.cutout());
    }
}
