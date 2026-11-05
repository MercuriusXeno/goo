package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;

/**
 * The one stratum every open dropdown, options list and goo type picker draws on: the screen lifts it after every pane
 * and chrome draw and before the dialog and the tooltip, so nothing a pane or the chrome draws lands over an open list
 * (decision dropdowns-draw-on-one-stratum-above-everything-but-tooltip-and-dialog). A pane names its overlays as
 * layers and never draws them inside its own extract.
 */
final class OverlayStratum {
    private OverlayStratum() {
    }

    /**
     * One thing a pane stands over everything it and the chrome drew: a builder's lists or a pane's own overlays.
     */
    @FunctionalInterface
    interface Layer {
        /**
         * @param graphics the frame's graphics
         * @param font     the font
         * @param targets  the frame's click areas
         */
        void draw(GuiGraphicsExtractor graphics, Font font, ClickTargets targets);
    }

    /**
     * Lifts the overlay stratum over everything drawn so far and draws each layer on it, in order; each layer's click
     * areas land after every pane's and the chrome's, so an open list takes the click over it.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param targets  the frame's click areas
     * @param layers   the active pane's overlays
     */
    static void drawOverlays(GuiGraphicsExtractor graphics, Font font, ClickTargets targets, List<Layer> layers) {
        graphics.nextStratum();
        for (Layer layer : layers) {
            layer.draw(graphics, font, targets);
        }
    }
}
