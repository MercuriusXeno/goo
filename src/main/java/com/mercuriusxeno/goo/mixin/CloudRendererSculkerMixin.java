package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.SculkerVision;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fades the clouds out under Sculker's vision, which draws before them and
 * so never dims them: they go dark with the rest of the far world as the
 * vision eases in and draw not at all while it stands full.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererSculkerMixin {

    /**
     * Draws no clouds under the full vision.
     *
     * @param info the callback
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void goo$noCloudsUnderTheVision(CallbackInfo info) {
        if (SculkerVision.hidesClouds(SculkerVision.presence())) {
            info.cancel();
        }
    }

    /**
     * Fades the clouds' color as the vision eases in.
     *
     * @param color the clouds' own color, ARGB
     * @return the color they draw in
     */
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int goo$fadeCloudsWithTheVision(int color) {
        return SculkerVision.cloudColor(color, SculkerVision.presence());
    }
}
