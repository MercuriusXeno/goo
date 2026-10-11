package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.PrismCrystal;
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
     * @param state         the prism's render state, the plain crystal's look and facing among it
     * @param poseStack     the pose at the prism's cell corner, scaled by the prism's growth
     * @param nodeCollector the submit collector
     */
    void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector);

    /**
     * How far the combo draws out of the prism along the face it grew from,
     * so the renderer keeps drawing it while the prism itself is off screen.
     *
     * @return the reach in blocks, none by default
     */
    default int beamReach() {
        return 0;
    }

    /**
     * The sides the combined prism's column stands with at rest
     * (decision relay-and-metronome-read-apart-at-rest).
     *
     * @return six by default, the plain column's
     */
    default PrismCrystal.ColumnSides restingSides() {
        return PrismCrystal.ColumnSides.SIX;
    }
}
