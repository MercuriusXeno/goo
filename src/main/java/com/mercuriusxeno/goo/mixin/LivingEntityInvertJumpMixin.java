package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.nether.CeilingGround;
import com.mercuriusxeno.goo.ability.nether.Invert;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * A living entity whose gravity Invert turns over jumps off the ceiling, down
 * and away from it, at the power it jumps off a floor.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityInvertJumpMixin {

    /**
     * Sets an inverted entity's jump speed away from the ceiling.
     *
     * @param jumpPower the jump's power
     * @param currentY  the entity's vertical speed
     * @param original  vanilla's choice of the faster upward speed
     * @return the vertical speed after the jump
     */
    @WrapOperation(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(DD)D"))
    private double goo$jumpsOffTheCeiling(double jumpPower, double currentY, Operation<Double> original) {
        return Invert.isInverted((LivingEntity) (Object) this)
                ? CeilingGround.jumpSpeed(jumpPower, currentY, true)
                : original.call(jumpPower, currentY);
    }
}
