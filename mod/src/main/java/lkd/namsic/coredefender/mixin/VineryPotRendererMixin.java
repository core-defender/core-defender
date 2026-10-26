package lkd.namsic.coredefender.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import lkd.namsic.coredefender.client.OilPotRenderer;
import lkd.namsic.coredefender.compat.VineryOil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.satisfy.vinery.client.render.block.GrapevinePotRenderer", remap = false)
// Registered entry point: invoked by the game registry, scripts, or annotation discovery.
@SuppressWarnings("unused")
abstract class VineryPotRendererMixin {
    @Inject(method = "render(Lnet/satisfy/vinery/core/block/entity/GrapevinePotBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At("HEAD"), cancellable = true)
    private void coredefender$oilRender(@Coerce Object pot, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                        int light, int overlay, CallbackInfo ci) {
        BlockEntity entity = (BlockEntity) pot;
        if (VineryOil.active(entity)) {
            OilPotRenderer.render(entity, partialTick, pose, buffers, light, overlay);
            ci.cancel();
        }
    }
}
