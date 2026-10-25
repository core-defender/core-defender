package lkd.namsic.coredefender.client;

import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import lkd.namsic.coredefender.CoreDefender;
import lkd.namsic.coredefender.registry.ModBlockEntities;
import lkd.namsic.coredefender.turret.BasicTurretBlockEntity;
import lkd.namsic.coredefender.turret.TurretPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 고정 받침대 위에서 회전하는 석궁. 바닐라 텍스처를 재사용한다. */
@EventBusSubscriber(modid = CoreDefender.MOD_ID, value = Dist.CLIENT)
public final class BasicTurretRenderer implements BlockEntityRenderer<BasicTurretBlockEntity> {
    private static final BlockState WOOD = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState METAL = Blocks.IRON_BLOCK.defaultBlockState();
    private static final BlockState STRING = Blocks.WHITE_WOOL.defaultBlockState();
    private final BlockRenderDispatcher blocks;

    public BasicTurretRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.BASIC_TURRET.get(), BasicTurretRenderer::new);
    }

    @Override
    public AABB getRenderBoundingBox(BasicTurretBlockEntity turret) {
        return new AABB(turret.getBlockPos()).inflate(1);
    }

    @Override
    public void render(BasicTurretBlockEntity turret, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (turret.getLevel() == null) return;
        double now = turret.getLevel().getGameTime() + partialTick;
        float reload = (float) Math.clamp((now - turret.getReloadStarted()) / Math.max(1,turret.clientInterval), 0, 1);
        float draw = turret.isLoaded() ? reload * reload * (3 - 2 * reload) : 0;
        float shotAge = (float) (now - turret.getLastShot());
        float recoil = shotAge >= 0 && shotAge < 5 ? .10F * (1 - shotAge / 5) : 0;

        pose.pushPose();
        boolean warning = turret.status == 2 || turret.status == 3 || turret.status == 4 || turret.status == 8 || turret.status == 9;
        BlockState lamp = (warning ? Blocks.REDSTONE_BLOCK : turret.clientAmpWarning || turret.outputBlocked ? Blocks.GOLD_BLOCK
                : turret.status == 7 ? Blocks.GRAY_CONCRETE : Blocks.EMERALD_BLOCK).defaultBlockState();
        this.box(pose,buffers,lamp,.38F,.18F,.02F,.24F,.16F,.08F,15728880,overlay);
        pose.translate(.5, 0, .5);
        this.box(pose, buffers, METAL, -.18F, .75F, -.18F, .36F, .38F, .36F, light, overlay);
        pose.translate(0, 1.3, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-turret.renderYaw(partialTick)));
        this.box(pose, buffers, METAL, -.25F, -.2F, -.22F, .5F, .12F, .44F, light, overlay);
        pose.mulPose(Axis.XP.rotationDegrees(turret.renderPitch(partialTick)));
        pose.translate(0, 0, -recoil);
        if (turret.kind() == 1) {
            this.box(pose,buffers,WOOD,-.30F,-.08F,-.48F,.60F,.18F,.98F,light,overlay);
            this.box(pose,buffers,METAL,-.34F,-.14F,-.15F,.12F,.42F,.20F,light,overlay);
            this.box(pose,buffers,METAL,.22F,-.14F,-.15F,.12F,.42F,.20F,light,overlay);
            pose.mulPose(Axis.XP.rotationDegrees(-45 + draw * 45));
            this.box(pose,buffers,WOOD,-.07F,0,-.4F,.14F,.12F,1.1F,light,overlay);
            this.box(pose,buffers,METAL,-.22F,.06F,.38F,.44F,.08F,.36F,light,overlay);
            if(turret.isLoaded())this.box(pose,buffers,Blocks.COBBLESTONE.defaultBlockState(),-.15F,.14F,.41F,.30F,.28F,.30F,light,overlay);
            pose.popPose();return;
        }
        if (turret.kind() == 2) {
            this.box(pose,buffers,Blocks.COPPER_BLOCK.defaultBlockState(),-.28F,-.18F,-.38F,.56F,.42F,.56F,light,overlay);
            this.box(pose,buffers,METAL,-.12F,-.07F,.12F,.24F,.24F,.58F,light,overlay);
            this.box(pose,buffers,Blocks.BLACKSTONE.defaultBlockState(),-.16F,-.11F,.65F,.32F,.32F,.12F,light,overlay);
            if(shotAge>=0&&shotAge<10)this.box(pose,buffers,Blocks.ORANGE_STAINED_GLASS.defaultBlockState(),-.08F,-.03F,.78F,.16F,.16F,.20F,15728880,overlay);
            pose.popPose();return;
        }
        this.box(pose, buffers, WOOD, -.10F, -.08F, -.55F, .20F, .16F, 1.05F, light, overlay);
        this.box(pose, buffers, METAL, -.13F, -.10F, -.15F, .26F, .20F, .12F, light, overlay);
        this.box(pose, buffers, METAL, -.035F, .08F, -.45F, .07F, .025F, .95F, light, overlay);

        // 양쪽 활대가 장전 시 뒤로 휘고, 시위 중앙이 화살 꼬리까지 당겨진다.
        float tipZ = .28F - draw * .12F;
        float stringZ = .22F - draw * .62F;
        for (int side : new int[]{-1, 1}) {
            this.beam(pose, buffers, WOOD, 0, 0, .3F, side * .37F, 0, .34F, .085F, light, overlay);
            this.beam(pose, buffers, WOOD, side * .37F, 0, .34F, side * .66F, 0, tipZ, .065F, light, overlay);
            this.beam(pose, buffers, STRING, side * .66F, .04F, tipZ, 0, .04F, stringZ, .018F, light, overlay);
        }
        // 발사 직후 4틱은 화살을 숨기고, 다음 화살이 위에서 레일로 내려온다.
        if (turret.isLoaded() && reload >= .2F) {
            float feed = Math.clamp((reload - .2F) / .5F, 0, 1);
            pose.pushPose();
            pose.translate(0, TurretPose.ARROW_HEIGHT + (1 - feed) * .22, -.18 * (1 - feed));
            this.box(pose, buffers, WOOD, -.014F, -.014F, -.4F, .028F, .028F, .93F, light, overlay);
            this.beam(pose, buffers, METAL, -.065F, 0, .5F, 0, 0, TurretPose.ARROW_TIP, .035F, light, overlay);
            this.beam(pose, buffers, METAL, .065F, 0, .5F, 0, 0, TurretPose.ARROW_TIP, .035F, light, overlay);
            this.box(pose, buffers, STRING, -.07F, -.009F, -.4F, .14F, .018F, .13F, light, overlay);
            this.box(pose, buffers, STRING, -.009F, -.06F, -.4F, .018F, .12F, .13F, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }

    private void beam(PoseStack pose, MultiBufferSource buffers, BlockState state,
                      float x0, float y0, float z0, float x1, float y1, float z1, float thickness, int light, int overlay) {
        Vector3f direction = new Vector3f(x1 - x0, y1 - y0, z1 - z0);
        float length = direction.length();
        pose.pushPose();
        pose.translate(x0, y0, z0);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), direction.normalize()));
        this.box(pose, buffers, state, -thickness / 2, -thickness / 2, 0, thickness, thickness, length, light, overlay);
        pose.popPose();
    }

    private void box(PoseStack pose, MultiBufferSource buffers, BlockState state,
                     float x, float y, float z, float width, float height, float depth, int light, int overlay) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(width, height, depth);
        // NeoForge explicitly treats null as the model-selected render type.
        //noinspection DataFlowIssue
        this.blocks.renderSingleBlock(state, pose, buffers, light, overlay, ModelData.EMPTY, null);
        pose.popPose();
    }
}
