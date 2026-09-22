package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.GooTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the crucible's dominant-type crossfade: after the debounce
 * commits a switch, the alpha the renderer hands the submitter climbs from
 * fully outgoing to fully shown over the blend.
 */
class DominantTypeFaderTest {

    /** Ticks the debounce needs before a new type is shown. */
    private static final int DEBOUNCE_TICKS = 20;

    /**
     * Holds ROCK for the debounce, then reports BLAZE until the fader
     * commits the switch, and answers the tick to continue from.
     *
     * @param fader the fader under test
     * @return the next game tick to feed
     */
    private static long switchRockToBlaze(DominantTypeFader fader) {
        long tick = 0;
        fader.tick(GooTypes.ROCK, tick++);
        for (int i = 0; i < DEBOUNCE_TICKS; i++) {
            fader.tick(GooTypes.BLAZE, tick++);
        }
        return tick;
    }

    /**
     * The tick that commits the switch starts the crossfade fully outgoing:
     * ROCK is the outgoing type, BLAZE the shown type, alpha zero.
     */
    @Test
    void committedSwitchStartsFullyOutgoing() {
        DominantTypeFader fader = new DominantTypeFader();

        switchRockToBlaze(fader);

        assertEquals(GooTypes.BLAZE, fader.getShownType());
        assertEquals(GooTypes.ROCK, fader.getOutgoingType());
        assertEquals(0f, fader.getCrossfadeAlpha(), 0.0001f);
    }

    /**
     * Mid-blend, the alpha lies strictly between the two endpoints, so the
     * outgoing quad at 1 - alpha and the incoming quad at alpha both draw.
     */
    @Test
    void midFadeAlphaLiesBetweenEndpoints() {
        DominantTypeFader fader = new DominantTypeFader();
        long tick = switchRockToBlaze(fader);

        for (int i = 0; i < 10; i++) {
            fader.tick(GooTypes.BLAZE, tick++);
        }

        float alpha = fader.getCrossfadeAlpha();
        assertTrue(alpha > 0f && alpha < 1f, "mid-fade alpha " + alpha);
        assertEquals(0.5f, alpha, 0.0001f);
        assertEquals(GooTypes.ROCK, fader.getOutgoingType());
    }

    /**
     * Once the alpha reaches one the outgoing type clears, so the renderer
     * submits the shown type alone.
     */
    @Test
    void finishedFadeClearsOutgoing() {
        DominantTypeFader fader = new DominantTypeFader();
        long tick = switchRockToBlaze(fader);

        for (int i = 0; i < DEBOUNCE_TICKS; i++) {
            fader.tick(GooTypes.BLAZE, tick++);
        }

        assertEquals(1f, fader.getCrossfadeAlpha(), 0.0001f);
        assertNull(fader.getOutgoingType());
    }

    /**
     * A repeated call on the same game tick advances nothing.
     */
    @Test
    void sameTickAdvancesNothing() {
        DominantTypeFader fader = new DominantTypeFader();
        long tick = switchRockToBlaze(fader);

        fader.tick(GooTypes.BLAZE, tick);
        float once = fader.getCrossfadeAlpha();
        fader.tick(GooTypes.BLAZE, tick);

        assertEquals(once, fader.getCrossfadeAlpha(), 0.0001f);
    }
}
