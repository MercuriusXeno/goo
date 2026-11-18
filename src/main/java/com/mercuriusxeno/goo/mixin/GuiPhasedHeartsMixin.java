package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.hud.PhasedHearts;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws a phased player's normal hearts blue and see-through: vanilla's
 * heart draw takes the phased sprite in place of the red one, over the
 * container vanilla already drew, whole or half as vanilla's would have been.
 * phased-things-draw-as-a-ghostly-image
 */
@Mixin(Gui.class)
public abstract class GuiPhasedHeartsMixin {

    private static final int HEART_SIZE = 9;

    /**
     * Draws the phased heart in place of a normal one while the player stands out of phase.
     *
     * @param graphics   the gui graphics
     * @param type       the heart's type
     * @param xo         the heart's left edge
     * @param yo         the heart's top edge
     * @param isHardcore whether the world is hardcore
     * @param blinks     whether the heart blinks
     * @param half       whether it is a half heart
     * @param ci         the callback, cancelled once the phased heart draws
     */
    @Inject(method = "extractHeart", at = @At("HEAD"), cancellable = true)
    private void goo$phasedHeart(GuiGraphicsExtractor graphics, Gui.HeartType type, int xo, int yo,
                                 boolean isHardcore, boolean blinks, boolean half, CallbackInfo ci) {
        if (type == Gui.HeartType.NORMAL && PhasedHearts.drawPhased()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PhasedHearts.sprite(half), xo, yo, HEART_SIZE,
                    HEART_SIZE);
            ci.cancel();
        }
    }
}
