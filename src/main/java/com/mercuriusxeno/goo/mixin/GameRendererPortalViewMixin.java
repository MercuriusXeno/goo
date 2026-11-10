package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.ClientGatePassage;
import com.mercuriusxeno.goo.client.ability.PortalView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries the local player through a gate its eye crossed before the frame
 * places its camera, then draws the view through each visible linked portal
 * before the frame's own extraction, which then fills the level render state
 * for the main camera afresh.
 * portals-link-by-looking-through-portal-vision
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererPortalViewMixin {

    /**
     * Carries the local player through a gate ahead of the camera's placing.
     *
     * @param deltaTracker     the frame's delta tracker
     * @param advanceGameTime  whether the frame draws the level
     * @param ci               the callback
     */
    @Inject(method = "update", at = @At("HEAD"))
    private void goo$carryBeforeTheCamera(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        // A carry after the camera is placed draws its frame from the eye before it, against the entered gate,
        // whose view looks out from inside the carried player's own head.
        ClientGatePassage.crossBeforeFrame(deltaTracker);
    }

    /**
     * Draws the portal views ahead of the frame's extraction.
     *
     * @param deltaTracker     the frame's delta tracker
     * @param advanceGameTime  whether the frame draws the level
     * @param ci               the callback
     */
    @Inject(method = "extract", at = @At("HEAD"))
    private void goo$drawPortalViews(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        PortalView.drawBeforeExtract(deltaTracker, advanceGameTime);
    }
}
