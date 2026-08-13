package net.mehvahdjukaar.moonlight.core.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mehvahdjukaar.moonlight.api.block.IBeeGrowable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//lets IBeeGrowable blocks grow themselves when a bee pollinates them. mainly for double crops, where
//vanilla would only set the state of the half it found. wrapping the block set keeps vanilla's particles
//and its crop counter, so the bee still stops after the usual amount of crops
@Mixin(targets = "net.minecraft.world.entity.animal.bee.Bee$BeeGrowCropGoal")
public abstract class BeeGoalMixin {

    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean moonlight$growBeeGrowable(Level level, BlockPos pos, BlockState grownState,
                                             Operation<Boolean> original) {
        if (grownState.getBlock() instanceof IBeeGrowable beeGrowable) {
            return beeGrowable.getPollinated(level, pos, grownState);
        }
        return original.call(level, pos, grownState);
    }
}
