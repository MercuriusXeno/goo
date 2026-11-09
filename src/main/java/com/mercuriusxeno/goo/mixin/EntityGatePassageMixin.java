package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * A body passing through a gate is not in a wall: the suffocation check
 * reads the gate's blocks as solid, though they stand open to the body, and
 * would hurt it while it crosses, and the light inside the wall would draw
 * it dark.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 */
@Mixin(Entity.class)
public abstract class EntityGatePassageMixin {

    /**
     * Answers not in a wall for a body passing through a gate.
     *
     * @param cir the callback
     */
    @Inject(method = "isInWall", at = @At("HEAD"), cancellable = true)
    private void goo$notInWallWhilePassing(CallbackInfoReturnable<Boolean> cir) {
        if (GatePassage.passing((Entity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Reads a passing body's light from just before the gate's face, so it is
     * never drawn dark while its eye stands inside the wall.
     *
     * @param probe the probe the method answered
     * @return the probe the body is lit from
     */
    @ModifyReturnValue(method = "getLightProbePosition", at = @At("RETURN"))
    private Vec3 goo$litFromBeforeTheGate(Vec3 probe) {
        return GatePassage.litFrom((Entity) (Object) this, probe);
    }
}
