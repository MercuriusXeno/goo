package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.ConjuredDoor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers what shows for a dimension change into the pocket: the starfield
 * cover while the cover holds, vanilla's receiving screen with its own
 * reason while it does not
 * (decision doorway-starfield-fills-the-view-until-the-room-is-ready).
 */
class EntryScreensTest {

    private static final int LOCAL = 7;
    private static final int CHARGE = 20;
    private static final LevelLoadingScreen STARFIELD = mock(LevelLoadingScreen.class);
    private static final LevelLoadingScreen VANILLA = mock(LevelLoadingScreen.class);

    @Test
    void aChangeWhileTheCoverHoldsShowsTheStarfield() {
        EntryCover cover = new EntryCover();
        cover.doorSeen(LOCAL, LOCAL, ConjuredDoor.standing(Vec3.ZERO, 0f, CHARGE, CHARGE));

        LevelLoadingScreen shown = screensOver(cover).create(mock(LevelLoadTracker.class),
                LevelLoadingScreen.Reason.OTHER);

        assertSame(STARFIELD, shown);
        assertTrue(cover.covering(), "the cover holds on past the change");
    }

    @Test
    void aChangeWithNoCoverShowsVanillasScreen() {
        LevelLoadingScreen shown = screensOver(new EntryCover()).create(mock(LevelLoadTracker.class),
                LevelLoadingScreen.Reason.NETHER_PORTAL);

        assertSame(VANILLA, shown);
    }

    private static EntryScreens screensOver(EntryCover cover) {
        return new EntryScreens(cover, (tracker, reason) -> STARFIELD,
                (tracker, reason) -> reason == LevelLoadingScreen.Reason.NETHER_PORTAL ? VANILLA : null);
    }
}
