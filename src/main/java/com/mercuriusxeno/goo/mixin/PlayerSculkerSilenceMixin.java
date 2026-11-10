package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.deep.SculkerSilence;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips a silent player's step sound, which the player plays on its own.
 * sculker-mutes-your-movement-sounds
 */
@Mixin(Player.class)
public abstract class PlayerSculkerSilenceMixin {

    /**
     * Skips the step sound of a player moving in silence.
     *
     * @param info the callback
     */
    @Inject(method = "playStepSound", at = @At("HEAD"), cancellable = true)
    private void goo$silentStep(CallbackInfo info) {
        if (SculkerSilence.movesSilently((Player) (Object) this)) {
            info.cancel();
        }
    }
}
