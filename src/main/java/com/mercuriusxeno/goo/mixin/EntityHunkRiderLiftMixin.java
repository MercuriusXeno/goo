package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.entity.HunkRiders;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lifts a rider of Build's rising stone once its tick has taken its last position: Minecraft 26.3 takes it inside
 * commonTick, after the entity tick event goo lifted riders on in 26.2, so the lift lands at commonTick's tail
 * and the climb still falls inside the rider's interpolation.
 * decision spire-carries-its-riders-up-with-the-rising-stone
 */
@Mixin(Entity.class)
public abstract class EntityHunkRiderLiftMixin {

    /**
     * @param ci the callback
     */
    @Inject(method = "commonTick", at = @At("TAIL"))
    private void goo$liftOntoAClimbingHunk(CallbackInfo ci) {
        HunkRiders.liftAtTickStart((Entity) (Object) this);
    }
}
