package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wash a durative self ability lays over the view as its use lands:
 * one even tint opening at the middle and washing past every edge, then
 * emptying from the middle out until the view is clear by its named
 * duration's end.
 * durative-self-abilities-wash-over-the-view-in-place-of-eating
 */
class DurativeWashTest {

    private static final int SAMPLES = 48;
    private static final float DELTA = 1e-6f;

    @Test
    void theWashIsNothingAtItsStartAndItsEnd() {
        assertEquals(0f, DurativeWash.strengthAt(0), DELTA);
        assertEquals(0f, DurativeWash.strengthAt(DurativeWash.WASH_TICKS), DELTA);
    }

    @Test
    void betweenItsStartAndItsEndTheWashCoversTheView() {
        for (int sample = 1; sample < SAMPLES; sample++) {
            double ticks = (double) DurativeWash.WASH_TICKS * sample / SAMPLES;

            assertTrue(DurativeWash.strengthAt(ticks) > 0f, "the wash should show at tick " + ticks);
        }
    }

    @Test
    void theWashRisesToItsFullStrengthAndFallsBack() {
        float peak = 0f;
        for (int sample = 0; sample <= SAMPLES; sample++) {
            peak = Math.max(peak, DurativeWash.strengthAt((double) DurativeWash.WASH_TICKS * sample / SAMPLES));
        }

        assertEquals(1f, peak, 0.05f, "the wash should reach its full strength");
    }

    private static final int WIDTH = 480;
    private static final int HEIGHT = 270;
    private static final double EDGE = 1e-9;

    /** The tick the fill has covered the view and the empty begins. */
    private static final double FILLED = DurativeWash.WASH_TICKS * DurativeWash.SWELL_SHARE;

    /** operator ruling: "I'd like it to open up in the middle and wash *past* the edges of the screen uniformly" */
    @Test
    void theFillOpensInTheMiddleOfTheView() {
        DurativeWash.Box opening = DurativeWash.fillAt(1, WIDTH, HEIGHT);

        assertEquals(WIDTH / 2.0, (opening.left() + opening.right()) / 2, EDGE, "the fill should open centered");
        assertEquals(HEIGHT / 2.0, (opening.top() + opening.bottom()) / 2, EDGE, "the fill should open centered");
        assertTrue(opening.right() - opening.left() > 0 && opening.right() - opening.left() < WIDTH / 2.0,
                "early on the fill should be small, " + opening);
    }

    @Test
    void theFillWashesPastEveryEdgeBeforeItEmpties() {
        DurativeWash.Box filled = DurativeWash.fillAt(FILLED, WIDTH, HEIGHT);
        DurativeWash.Box hollow = DurativeWash.hollowAt(FILLED, WIDTH, HEIGHT);

        assertTrue(filled.left() < 0 && filled.top() < 0 && filled.right() > WIDTH && filled.bottom() > HEIGHT,
                "the fill should wash past every edge, " + filled);
        assertEquals(0, hollow.right() - hollow.left(), EDGE, "nothing should have emptied yet, " + hollow);
    }

    /** operator ruling: "1 until it fills, and then it empties from the middle out" */
    @Test
    void theWashEmptiesFromTheMiddleOutToTheEdges() {
        DurativeWash.Box emptying = DurativeWash.hollowAt((FILLED + DurativeWash.WASH_TICKS) / 2, WIDTH, HEIGHT);
        DurativeWash.Box emptied = DurativeWash.hollowAt(DurativeWash.WASH_TICKS, WIDTH, HEIGHT);

        assertEquals(WIDTH / 2.0, (emptying.left() + emptying.right()) / 2, EDGE, "the hollow should open centered");
        assertTrue(emptying.left() > 0 && emptying.right() < WIDTH, "part way the hollow should stand inside the view, "
                + emptying);
        assertTrue(emptied.left() <= 0 && emptied.top() <= 0 && emptied.right() >= WIDTH && emptied.bottom() >= HEIGHT,
                "by the wash's end the hollow should reach every edge, " + emptied);
    }

    /** A small step in reach, against the view's half size. */
    private static final double NUDGE = 0.01;

    /** operator ruling: "it is far too monotone, needs a gradient, a fade, something more subtle" */
    @Test
    void theFillsFrontFadesInRatherThanCuttingOff() {
        double ticks = FILLED / 2;
        double front = DurativeWash.fillScaleAt(ticks);
        float atTheFront = DurativeWash.alphaAt(front - NUDGE, ticks);
        float behindIt = DurativeWash.alphaAt(front * 0.2, ticks);

        assertTrue(atTheFront > 0f && atTheFront < behindIt, "just inside the fill's front the tint " + atTheFront
                + " should fade in toward " + behindIt + " behind it");
        assertEquals(0f, DurativeWash.alphaAt(front + NUDGE, ticks), DELTA, "past the front nothing should show");
    }

    @Test
    void theHollowsEdgeFadesOutRatherThanCuttingOff() {
        double ticks = (FILLED + DurativeWash.WASH_TICKS) / 2;
        double edge = DurativeWash.hollowScaleAt(ticks);
        float atTheEdge = DurativeWash.alphaAt(edge + NUDGE, ticks);
        float pastIt = DurativeWash.alphaAt(Math.min(1, edge + 0.3), ticks);

        assertTrue(atTheEdge > 0f && atTheEdge < pastIt, "just outside the hollow the tint " + atTheEdge
                + " should fade in toward " + pastIt);
        assertEquals(0f, DurativeWash.alphaAt(edge * 0.5, ticks), DELTA, "inside the hollow nothing should show");
    }

    @Test
    void theTintCrestsBrightAtTheMovingFrontAndDeepensBehindIt() {
        double ticks = FILLED / 2;
        double front = DurativeWash.fillScaleAt(ticks);

        assertTrue(DurativeWash.crestAt(front - NUDGE, ticks) > DurativeWash.crestAt(front * 0.2, ticks),
                "the tint should run brighter at the moving front than behind it");
    }

    /** operator ruling: "Too opaque" */
    @Test
    void theWashAtItsFullestStandsLessOpaqueThanBefore() {
        assertTrue(DurativeWash.peakOpacity() < 0.7f, "the wash's peak opacity " + DurativeWash.peakOpacity()
                + ", its layers stacked, should stand below 0.7");
    }

    @Test
    void pastItsEndTheWashIsGone() {
        assertEquals(0f, DurativeWash.strengthAt(DurativeWash.WASH_TICKS + 1.0), DELTA);
    }
}
