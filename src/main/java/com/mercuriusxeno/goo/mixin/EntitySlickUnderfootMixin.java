package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * An entity standing on Kinetic's slick or Crystal's shatter stands on that
 * cell. Vanilla reads the block a step, a landing and the ground's friction
 * act through below the feet, deeper than a thin cell's top, so it would
 * hand them to the block under the goo; the cell holding the entity up takes
 * them instead, so the slick's friction and ramp, and the shatter's cut,
 * reach what moves on it at any height.
 * slick-coats-the-ground-with-kinetic-speed
 * each-shatter-cut-has-a-chance-to-deplete-a-layer
 */
@Mixin(Entity.class)
public abstract class EntitySlickUnderfootMixin {

    /**
     * Answers the slick or shatter cell holding the entity up, where one does.
     *
     * @param onPos vanilla's block under the feet at the offset asked
     * @return the cell the entity stands on, else vanilla's block
     */
    @ModifyReturnValue(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("RETURN"))
    private BlockPos goo$standsOnTheSlick(BlockPos onPos) {
        Entity self = (Entity) (Object) this;
        return self.mainSupportingBlockPos
                .filter(supporting -> self.level().getBlockState(supporting).getBlock() instanceof VolumeCellBlock cell
                        && cell.surface().takesTheStepAtAnyHeight())
                .orElse(onPos);
    }
}
