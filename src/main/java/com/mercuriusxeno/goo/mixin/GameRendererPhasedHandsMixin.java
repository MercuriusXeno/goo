package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.PhasedHands;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ends a phased player's hand fade as the first-person hands finish drawing,
 * so nothing the frame draws after them, the gui's slots above all, fades.
 * phased-things-draw-as-a-ghostly-image
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPhasedHandsMixin {

    /**
     * Ends the hand fade once the hands have drawn.
     *
     * @param ci the callback
     */
    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void goo$endPhasedHands(CallbackInfo ci) {
        PhasedHands.endHands();
    }
}
