package com.mercuriusxeno.goo.client.zone;

import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.neoforged.neoforge.client.DimensionTransitionScreenManager.ReceivingLevelScreenFactory;

/**
 * What shows for a level change into the pocket: the starfield cover while
 * the local caster's entry holds it, marking the change it waited on, and
 * vanilla's receiving screen, with its own reason, for any other change.
 * decision doorway-starfield-fills-the-view-until-the-room-is-ready
 *
 * @param cover     the cover the entry holds
 * @param starfield makes the starfield cover's screen
 * @param vanilla   makes vanilla's receiving screen
 */
record EntryScreens(EntryCover cover, ReceivingLevelScreenFactory starfield, ReceivingLevelScreenFactory vanilla)
        implements ReceivingLevelScreenFactory {

    @Override
    public LevelLoadingScreen create(LevelLoadTracker tracker, LevelLoadingScreen.Reason reason) {
        if (!cover.covering()) {
            return vanilla.create(tracker, reason);
        }
        cover.levelChanged();
        return starfield.create(tracker, reason);
    }
}
