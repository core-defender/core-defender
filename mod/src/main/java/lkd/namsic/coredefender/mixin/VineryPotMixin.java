package lkd.namsic.coredefender.mixin;

import lkd.namsic.coredefender.compat.VineryOil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@Pseudo
@Mixin(targets = "net.satisfy.vinery.core.block.GrapevinePotBlock", remap = false)
// Registered entry point: invoked by the game registry, scripts, or annotation discovery.
@SuppressWarnings("unused")
abstract class VineryPotMixin {
    @Inject(method = "getCollisionShape", at = @At("RETURN"), cancellable = true)
    private void coredefender$oilFloor(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                       CallbackInfoReturnable<VoxelShape> ci) {
        if (VineryOil.active(level.getBlockEntity(pos))) {
            ci.setReturnValue(Shapes.or(ci.getReturnValue(), Block.box(0, 0, 0, 16, 4, 16)));
        }
    }

    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void coredefender$oilStomp(Level level, BlockState state, BlockPos pos, Entity entity, float distance, CallbackInfo ci) {
        if (VineryOil.fallOn(level, pos, entity, distance)) {
            ci.cancel();
        }
    }

    @Inject(method = "onRemove", at = @At("HEAD"))
    private void coredefender$oilDrop(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved, CallbackInfo ci) {
        if (!state.is(next.getBlock())) {
            VineryOil.removed(level, pos);
        }
    }
}
