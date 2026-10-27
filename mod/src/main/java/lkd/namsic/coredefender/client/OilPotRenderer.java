package lkd.namsic.coredefender.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.compat.OilBatch;
import lkd.namsic.coredefender.compat.VineryOil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
public final class OilPotRenderer {
    private static final ModelResourceLocation SEEDS = model("oil_pot_seeds");
    private static final ModelResourceLocation MASH = model("oil_pot_mash");
    private static final ModelResourceLocation OIL = model("oil_pot_oil");

    private OilPotRenderer() {
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(CoreDefender.MOD_ID, "block/" + name));
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(SEEDS);
        event.register(MASH);
        event.register(OIL);
    }

    public static void render(BlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        OilBatch batch = VineryOil.batch(entity);
        CompoundTag tag = entity.getPersistentData().getCompound(VineryOil.KEY);
        double age = entity.getLevel() == null || !tag.contains("lastStomp") ? 20
            : entity.getLevel().getGameTime() + partialTick - tag.getLong("lastStomp");
        float squash = age >= 0 && age < 8 ? (float) Math.sin(age / 8 * Math.PI) * .035F : 0;
        float surface = .07F + .15F * batch.seeds() / OilBatch.SEEDS - squash;
        Minecraft mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(.08, .065, .08);
        pose.scale(.84F, Math.max(.01F, surface - .065F), .84F);
        drawModel(batch.stomps() > 0 ? MASH : SEEDS, RenderType.solid(), pose, buffers, light, overlay);
        pose.popPose();
        // 씨앗이 점차 잠기고, 압착이 끝나면 황금빛 액체 표면만 남는다.
        if (batch.stomps() > 0) {
            pose.pushPose();
            pose.translate(.08, surface, .08);
            pose.scale(.84F, .025F, .84F);
            drawModel(OIL, RenderType.translucent(), pose, buffers, light, overlay);
            pose.popPose();
        }

        if (!batch.ready()) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("seed"));
            if (id == null) {
                return;
            }

            ItemStack seed = new ItemStack(BuiltInRegistries.ITEM.get(id));
            int count = Math.min(5, batch.seeds());
            for (int i = 0; i < count; i++) {
                pose.pushPose();
                double angle = i * Math.PI * 2 / count;
                pose.translate(.5 + Math.cos(angle) * .22, surface + .035, .5 + Math.sin(angle) * .22);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.mulPose(Axis.ZP.rotationDegrees(i * 71));
                pose.scale(.3F, .3F, .3F);
                mc.getItemRenderer().renderStatic(seed, ItemDisplayContext.FIXED, light, overlay, pose, buffers, entity.getLevel(), i);
                pose.popPose();
            }
        }
    }

    private static void drawModel(ModelResourceLocation location, RenderType type, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
            pose.last(), buffers.getBuffer(type), null, model, 1F, 1F, 1F, light, overlay, ModelData.EMPTY, type);
    }
}
