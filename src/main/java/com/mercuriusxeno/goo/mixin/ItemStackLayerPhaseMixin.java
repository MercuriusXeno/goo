package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.PhasedHands;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * Draws a phased player's own held item translucent in first person: while
 * the phased player's hands draw, an item layer of plain quads submits them
 * under the phase fade in place of its own submit. A layer drawn by a special
 * renderer, as the glove is, fades through that renderer.
 * phased-things-draw-as-a-ghostly-image
 */
@Mixin(targets = "net.minecraft.client.renderer.item.ItemStackRenderState$LayerRenderState")
public abstract class ItemStackLayerPhaseMixin {

    private static final String SHADOWED = "shadowed by mixin";

    @Shadow
    @Final
    private List<BakedQuad> quads;

    @Shadow
    private @Nullable IntList tintLayers;

    @Shadow
    private @Nullable SpecialModelRenderer<Object> specialRenderer;

    @Shadow
    private void applyTransform(PoseStack.Pose localPose) {
        throw new AssertionError(SHADOWED);
    }

    /**
     * Submits the layer faded where a phased player's hands draw it.
     *
     * @param poseStack the pose stack
     * @param collector the render node collector
     * @param light     the packed light
     * @param overlay   the packed overlay
     * @param outline   the outline color
     * @param ci        the callback, cancelled once the layer submits faded
     */
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void goo$fadePhasedHeldItem(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay,
                                        int outline, CallbackInfo ci) {
        if (!PhasedHands.ghosting() || specialRenderer != null) {
            return;
        }
        poseStack.pushPose();
        applyTransform(poseStack.last());
        PhasedHands.submitFaded(poseStack, collector, quads,
                tintLayers != null ? tintLayers.toIntArray() : new int[0], light, overlay);
        poseStack.popPose();
        ci.cancel();
    }
}
