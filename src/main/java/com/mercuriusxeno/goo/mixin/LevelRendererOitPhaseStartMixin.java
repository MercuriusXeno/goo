package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.OitPhaseStart;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Readies goo's OIT draws as a level pass's OIT phase starts, before it opens any pass: NeoForge posts no event
 * between the opaque pass closing and the OIT stages opening theirs, and 26.3 refuses a depth copy inside either.
 * decision goo-translucency-draws-through-order-independent-transparency
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererOitPhaseStartMixin {

    /**
     * @param ci the callback
     */
    @Inject(method = "executeOit", at = @At("HEAD"))
    private void goo$readyTheOitPhase(CallbackInfo ci) {
        OitPhaseStart.beforeTheOitPhase();
    }
}
