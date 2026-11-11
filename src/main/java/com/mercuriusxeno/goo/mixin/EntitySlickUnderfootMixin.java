package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.KineticSlickBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * An entity standing on Kinetic's slick stands on the slick. Vanilla reads
 * the block a step, a landing and the ground's friction act through below the
 * feet, deeper than the sixteenth-thin film, so it would hand them to the
 * block under the slick; the slick holding the entity up takes them instead,
 * so its friction and its ramp reach what moves on it.
 * slick-coats-the-ground-with-kinetic-speed
 */
@Mixin(Entity.class)
public abstract class EntitySlickUnderfootMixin {

    /**
     * Answers the slick holding the entity up, where one does.
     *
     * @param onPos vanilla's block under the feet at the offset asked
     * @return the slick the entity stands on, else vanilla's block
     */
    @ModifyReturnValue(method = "getOnPos(F)Lnet/minecraft/core/BlockPos;", at = @At("RETURN"))
    private BlockPos goo$standsOnTheSlick(BlockPos onPos) {
        Entity self = (Entity) (Object) this;
        return self.mainSupportingBlockPos
                .filter(supporting -> self.level().getBlockState(supporting).getBlock() instanceof KineticSlickBlock)
                .orElse(onPos);
    }
}
