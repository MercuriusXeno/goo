package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.quantum.PhaseEvents;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets bodies on opposite sides of phase pass through each other: neither
 * pushes the other, and neither stands solid to the other, on both sides.
 * phase-shares-a-plane-between-the-phased
 */
@Mixin(Entity.class)
public abstract class EntityPhasePassMixin {

    /**
     * Skips a push between bodies across phase.
     *
     * @param pusher the body pushing this one
     * @param ci     the callback
     */
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void goo$passThroughAcrossPhase(Entity pusher, CallbackInfo ci) {
        if (PhaseEvents.acrossPhase((Entity) (Object) this, pusher)) {
            ci.cancel();
        }
    }

    /**
     * Stands no body solid to one across phase.
     *
     * @param other the body that would collide with this one
     * @param cir   the callback
     */
    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void goo$noSolidAcrossPhase(Entity other, CallbackInfoReturnable<Boolean> cir) {
        if (PhaseEvents.acrossPhase((Entity) (Object) this, other)) {
            cir.setReturnValue(false);
        }
    }
}
