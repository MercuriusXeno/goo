package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws one pane as the values manager draws it: the pane, then its overlays on the overlay stratum, then its dialog
 * (decision dropdowns-draw-on-one-stratum-above-everything-but-tooltip-and-dialog).
 */
final class ScreenDraw {
    private ScreenDraw() {
    }

    static void extract(ValuesPane pane, GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds,
                        ClickTargets targets) {
        pane.extract(graphics, font, bounds, targets);
        OverlayStratum.drawOverlays(graphics, font, targets, pane.overlayLayers());
    }

    static void extract(GroupsPane pane, GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds,
                        ClickTargets targets) {
        pane.extract(graphics, font, bounds, targets);
        OverlayStratum.drawOverlays(graphics, font, targets, pane.overlayLayers());
    }

    static void extract(ProcessesPane pane, GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds,
                        ClickTargets targets) {
        pane.extract(graphics, font, bounds, targets);
        OverlayStratum.drawOverlays(graphics, font, targets, pane.overlayLayers());
    }

    static void extract(VariablesPane pane, GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds,
                        ClickTargets targets, int mouseX, int mouseY) {
        pane.extract(graphics, font, bounds, targets, mouseX, mouseY);
        OverlayStratum.drawOverlays(graphics, font, targets, pane.overlayLayers());
        pane.drawDialog(graphics, font, targets);
    }
}
