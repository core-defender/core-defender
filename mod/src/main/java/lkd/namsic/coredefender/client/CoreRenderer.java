package lkd.namsic.coredefender.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.block.CoreBlockEntity;
import lkd.namsic.coredefender.registry.ModBlockEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
public final class CoreRenderer implements BlockEntityRenderer<CoreBlockEntity> {
    private static final ModelResourceLocation CRYSTAL = ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "block/core_crystal"));

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(CRYSTAL);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.CORE.get(), context -> new CoreRenderer());
    }

    @Override
    public AABB getRenderBoundingBox(CoreBlockEntity core) {
        return new AABB(core.getBlockPos()).expandTowards(0, .5, 0);
    }

    @Override
    public void render(CoreBlockEntity core, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (core.getLevel() == null) {
            return;
        }

        // 좌표로 위상을 어긋나게 해 여러 코어가 같은 박자로 움직이지 않게 한다.
        double time = core.getLevel().getGameTime() + partialTick + core.getBlockPos().asLong() % 360;
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(CRYSTAL);
        pose.pushPose();
        pose.translate(.5, .14 + Math.sin(time * .08) * .05, .5);
        pose.mulPose(Axis.YP.rotationDegrees((float) (time * 2 % 360)));
        pose.translate(-.5, 0, -.5);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()),
            null, model, 1F, 1F, 1F, LightTexture.FULL_BRIGHT, overlay, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }
}
