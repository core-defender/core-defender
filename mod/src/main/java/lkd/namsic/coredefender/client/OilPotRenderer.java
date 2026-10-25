package lkd.namsic.coredefender.client;

import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import lkd.namsic.coredefender.compat.OilBatch;
import lkd.namsic.coredefender.compat.VineryOil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
public final class OilPotRenderer {
    private OilPotRenderer() {}
    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()).toString().equals("vinery:grapevine_pot")) {
            event.getToolTip().add(Component.translatable("tooltip.coredefender.oil_pot").withStyle(ChatFormatting.GOLD));
            event.getToolTip().add(Component.translatable("tooltip.coredefender.oil_pot_controls").withStyle(ChatFormatting.GRAY));
            event.getToolTip().add(Component.translatable("tooltip.coredefender.oil_pot_break").withStyle(ChatFormatting.GRAY));
        } else if (event.getItemStack().is(ModItems.SEED_OIL_BOTTLE.get())) {
            event.getToolTip().add(Component.translatable("tooltip.coredefender.oil_bottle").withStyle(ChatFormatting.GRAY));
        }
    }
    public static void render(BlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        OilBatch batch = VineryOil.batch(entity);
        var tag = entity.getPersistentData().getCompound(VineryOil.KEY);
        double age = entity.getLevel() == null || !tag.contains("lastStomp") ? 20
                : entity.getLevel().getGameTime() + partialTick - tag.getLong("lastStomp");
        float squash = age >= 0 && age < 8 ? (float) Math.sin(age / 8 * Math.PI) * .035F : 0;
        float surface = .07F + .15F * batch.seeds() / OilBatch.SEEDS - squash;
        var mc = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(.08, .065, .08);
        pose.scale(.84F, Math.max(.01F, surface - .065F), .84F);
        // NeoForge explicitly treats null as the model-selected render type.
        //noinspection DataFlowIssue
        mc.getBlockRenderer().renderSingleBlock((batch.stomps() > 0 ? Blocks.YELLOW_TERRACOTTA : Blocks.HAY_BLOCK).defaultBlockState(),
                pose, buffers, light, overlay, ModelData.EMPTY, null);
        pose.popPose();
        // 씨앗이 점차 잠기고, 압착이 끝나면 황금빛 액체 표면만 남는다.
        if (batch.stomps() > 0) {
            pose.pushPose();
            pose.translate(.08, surface, .08);
            pose.scale(.84F, .025F, .84F);
            // NeoForge explicitly treats null as the model-selected render type.
            //noinspection DataFlowIssue
            mc.getBlockRenderer().renderSingleBlock(Blocks.HONEY_BLOCK.defaultBlockState(), pose, buffers, light, overlay, ModelData.EMPTY, null);
            pose.popPose();
        }
        if (!batch.ready()) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("seed"));
            if (id == null) return;
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
}
