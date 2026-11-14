package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.CellSurface;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * An entity standing on a slime coat lands on the coat. Vanilla finds the
 * block a landing, a fall and a step act on a fifth of a block under the
 * feet, which a thin slime cell's top lies above, so it would hand them to the
 * block under the coat, as a carpet on a slime block still bounces; the coat
 * holding the entity up takes them instead.
 * bounce-ramps-jump-height-while-jump-is-held
 */
@Mixin(Entity.class)
public abstract class EntitySlimeCoatUnderfootMixin {

    /**
     * Answers the coat holding the entity up, where one does.
     *
     * @param onPos vanilla's block a fifth of a block under the feet
     * @return the coat the entity stands on, else vanilla's block
     */
    @ModifyReturnValue(method = "getOnPosLegacy", at = @At("RETURN"))
    private BlockPos goo$landsOnTheCoat(BlockPos onPos) {
        Entity self = (Entity) (Object) this;
        return self.mainSupportingBlockPos
                .filter(supporting -> VolumeCellBlock.hasSurface(self.level().getBlockState(supporting).getBlock(),
                        CellSurface.BOUNCE))
                .orElse(onPos);
    }
}
