package lkd.namsic.coredefender.mixin;

import lkd.namsic.coredefender.wall.BarrierSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 퍼짐과 경사 탐색이 모두 이 판정을 거치므로 여기서 막으면 성벽 경계 너머로 흐르지 않는다.
 */
@Mixin(FlowingFluid.class)
// Registered entry point: invoked by the game registry, scripts, or annotation discovery.
@SuppressWarnings("unused")
abstract class FluidBarrierMixin {
    @Inject(method = "canPassThroughWall", at = @At("HEAD"), cancellable = true)
    private void coredefender$barrier(Direction direction, BlockGetter level, BlockPos from, BlockState fromState,
                                      BlockPos to, BlockState toState, CallbackInfoReturnable<Boolean> callback) {
        if (BarrierSystem.blocksFluid(level, from, to)) {
            callback.setReturnValue(false);
        }
    }
}
