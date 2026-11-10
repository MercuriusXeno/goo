package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.deep.SculkerSilence;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips a silent player's swim sound and its muffled and paired step sounds.
 * sculker-mutes-your-movement-sounds
 */
@Mixin(Entity.class)
public abstract class EntitySculkerSilenceMixin {

    /**
     * Skips the swim sound of a player moving in silence.
     *
     * @param volume the sound's volume
     * @param info   the callback
     */
    @Inject(method = "playSwimSound", at = @At("HEAD"), cancellable = true)
    private void goo$silentSwim(float volume, CallbackInfo info) {
        goo$skipWhenSilent(info);
    }

    /**
     * Skips the muffled step sound of a player moving in silence.
     *
     * @param info the callback
     */
    @Inject(method = "playMuffledStepSound", at = @At("HEAD"), cancellable = true)
    private void goo$silentMuffledStep(CallbackInfo info) {
        goo$skipWhenSilent(info);
    }

    /**
     * Skips the paired step sounds of a player moving in silence.
     *
     * @param info the callback
     */
    @Inject(method = "playCombinationStepSounds", at = @At("HEAD"), cancellable = true)
    private void goo$silentPairedSteps(CallbackInfo info) {
        goo$skipWhenSilent(info);
    }

    private void goo$skipWhenSilent(CallbackInfo info) {
        if (SculkerSilence.movesSilently((Entity) (Object) this)) {
            info.cancel();
        }
    }
}
