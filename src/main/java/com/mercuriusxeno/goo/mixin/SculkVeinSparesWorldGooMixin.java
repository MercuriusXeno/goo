package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A spreading sculk vein never grows into a world goo's volume cell: vanilla's
 * vein spread takes any state that reads replaceable, and the cell reads
 * replaceable for the landing rules, so a vein would delete the goo its layers
 * hold, dropping nothing, as building on it would without
 * VolumeCellBlock.canBeReplaced refusing a placed block.
 * sculk-charge-runs-along-host-goo-without-weakening
 * breaking-slime-drops-weird-goo-worth-its-layers
 */
@Mixin(targets = "net.minecraft.world.level.block.SculkVeinBlock$SculkVeinSpreaderConfig")
public abstract class SculkVeinSparesWorldGooMixin {

    /**
     * Refuses a vein the space a volume cell holds.
     *
     * @param level              the level
     * @param sourcePos          where the vein spreads from
     * @param placementPos       where the vein would grow
     * @param placementDirection the face the vein would cling to
     * @param existingState      what stands where the vein would grow
     * @param cir                the answer, false for a volume cell
     */
    @Inject(method = "stateCanBeReplaced", at = @At("HEAD"), cancellable = true)
    private void goo$sparesWorldGoo(BlockGetter level, BlockPos sourcePos, BlockPos placementPos,
                                    Direction placementDirection, BlockState existingState,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (existingState.getBlock() instanceof VolumeCellBlock) {
            cir.setReturnValue(false);
        }
    }
}
