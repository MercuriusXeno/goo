package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * How a prism holding one combo draws: the permanent thing a type's combo
 * grows on the prism, drawn in place of the plain crystal.
 * decision prism-hosts-the-combos
 */
@FunctionalInterface
public interface PrismComboStyle {

    /**
     * Draws the combined prism.
     *
     * @param state         the prism's render state, its plain crystal model among it
     * @param poseStack     the pose at the prism's cell corner, scaled by the prism's growth
     * @param nodeCollector the submit collector
     */
    void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector);
}
