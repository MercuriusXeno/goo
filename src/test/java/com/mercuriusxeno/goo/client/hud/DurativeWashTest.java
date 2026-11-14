package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wash a durative self ability lays over the view as its use lands:
 * nothing as it begins, rising to cover the viewport, and gone again by its
 * named duration's end.
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

    /**
     * operator ruling: "want it to wash over the screen as a rectangle. It's coming out of a cuboid so the
     * circular flare makes no sense"
     */
    @Test
    void theWashBeginsAsASquareTheBlobsSizeWhereTheBlobStands() {
        DurativeWash.Box start = DurativeWash.boxAt(0, WIDTH, HEIGHT, true);

        assertEquals(start.right() - start.left(), start.bottom() - start.top(), EDGE,
                "the wash should begin as the cuboid's square, " + start);
        assertTrue(start.right() - start.left() < Math.min(WIDTH, HEIGHT) / 4.0,
                "the wash should begin at the blob's size, " + start);
        assertTrue((start.left() + start.right()) / 2 > WIDTH / 2.0,
                "a right-hand glove's blob should stand right of the middle, " + start);
    }

    @Test
    void theWashGrowsAsARectangleUntilItCoversTheViewport() {
        DurativeWash.Box start = DurativeWash.boxAt(0, WIDTH, HEIGHT, true);
        DurativeWash.Box partWay = DurativeWash.boxAt(0.5, WIDTH, HEIGHT, true);

        assertEquals(new DurativeWash.Box(0, 0, WIDTH, HEIGHT), DurativeWash.boxAt(1, WIDTH, HEIGHT, true));
        assertTrue(partWay.left() < start.left() && partWay.top() < start.top() && partWay.right() > start.right()
                && partWay.bottom() > start.bottom(), "part way the wash should have grown out of the blob, "
                + partWay + " from " + start);
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
