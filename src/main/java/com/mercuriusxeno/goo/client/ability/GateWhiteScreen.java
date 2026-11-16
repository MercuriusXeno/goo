package com.mercuriusxeno.goo.client.ability;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;

/**
 * The screen held over the gate's level change in place of vanilla's terrain loading screen: plain white filling
 * the view, the white the trip flooded it with, with no text or progress over it. It closes on vanilla's own
 * readiness, and GateWhiteout's white carries on over the view until the arrival mark lands.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
public final class GateWhiteScreen extends LevelLoadingScreen {

    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    /**
     * Opens the white over a level change.
     *
     * @param tracker the change's load tracker, which says when the far end is ready
     * @param reason  vanilla's reason for the change
     */
    public GateWhiteScreen(LevelLoadTracker tracker, Reason reason) {
        super(tracker, reason);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(0, 0, width, height, OPAQUE_WHITE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // The white is the whole cover; nothing draws over it.
    }
}
