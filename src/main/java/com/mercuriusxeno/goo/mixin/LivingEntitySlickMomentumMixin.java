package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.SlickMomentum;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * A living thing carrying slick's momentum keeps slick's ice friction on the
 * bare ground it crosses, so a short unpainted gap between slick cells does
 * not scrub off the speed the slick built.
 * operator ruling 2026-11-14: conserve the momentum over raised slick cells and short bare gaps
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySlickMomentumMixin {

    /**
     * Answers at least slick's friction for the ground under a thing carrying slick's momentum.
     *
     * @param friction the friction of the block under its feet
     * @return the friction it moves with
     */
    @ModifyExpressionValue(method = "travelInAir", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getFriction(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;)F"))
    private float goo$keepsSlickFriction(float friction) {
        return SlickMomentum.frictionFor((LivingEntity) (Object) this, friction);
    }
}
