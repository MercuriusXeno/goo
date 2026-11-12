package com.mercuriusxeno.goo.client.outsider;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;

/**
 * The screen held over the change into the pocket in place of vanilla's
 * receiving screen: the End portal's starfield alone, filling the view, as
 * vanilla's End portal background draws it, with no text or progress over
 * it. It closes on vanilla's own readiness, the client standing in the
 * pocket with the room's chunks drawn, and lets the cover go as it leaves.
 * decision doorway-starfield-fills-the-view-until-the-room-is-ready
 */
public final class StarfieldCoverScreen extends LevelLoadingScreen {

    /**
     * Opens the cover over a level change.
     *
     * @param tracker the change's load tracker, which says when the room is ready
     * @param reason  vanilla's reason for the change, set aside for the starfield
     */
    public StarfieldCoverScreen(LevelLoadTracker tracker, Reason reason) {
        super(tracker, Reason.END_PORTAL);
    }

    @Override
    public void update(LevelLoadTracker tracker, Reason reason) {
        super.update(tracker, Reason.END_PORTAL);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // The starfield background is the whole cover; nothing draws over it.
    }

    @Override
    public void removed() {
        EntryCover.LOCAL.clear();
        super.removed();
    }
}
