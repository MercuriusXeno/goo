package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.block.ability.SlickMomentum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * A thing carrying slick's momentum steps up in the air as on the ground:
 * vanilla's collide steps a thing up onto a block within its step height only
 * from the ground, so one dropping off a raised slick cell meets the next
 * cell's side and stops dead; carried, it rises onto that cell and keeps its
 * speed.
 * operator ruling 2026-11-14: conserve the momentum over raised slick cells and short bare gaps
 */
@Mixin(Entity.class)
public abstract class EntitySlickMomentumMixin {

    /**
     * Answers grounded for the step-up where the thing carries slick's momentum.
     *
     * @param onGround whether vanilla holds the thing on the ground
     * @return true where it is grounded or carries slick's momentum
     */
    @ModifyExpressionValue(method = "collide", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;onGround()Z"))
    private boolean goo$stepsUpCarryingSlick(boolean onGround) {
        return onGround || SlickMomentum.carries((Entity) (Object) this);
    }
}
