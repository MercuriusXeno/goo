package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.mercuriusxeno.goo.client.ability.Afterimages.PULSES;
import static com.mercuriusxeno.goo.client.ability.Afterimages.PULSE_GAP_TICKS;
import static com.mercuriusxeno.goo.client.ability.ViewportRipples.INSET_PER_TICK;
import static com.mercuriusxeno.goo.client.ability.ViewportRipples.LINE_LIFE_TICKS;
import static com.mercuriusxeno.goo.client.ability.ViewportRipples.REACH_INSET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blinker's viewport ripple is a copy of their own afterimage ripple,
 * the window's frame its silhouette: its lines leave on the ripple's gaps
 * and move inward at an even pace to a short stop, started only for the
 * local player (decision viewport-frame-ripples-on-blink).
 */
class ViewportRipplesTest {

    private static final int LOCAL_PLAYER = 12;
    private static final long STARTED = 900;
    private static final int ENDER = 0x2A9D8F;
    private static final float EPSILON = 1e-5f;

    private static Afterimages.Afterimage<Void> viewportRipple() {
        ViewportRipples ripples = new ViewportRipples();
        ripples.onAfterimage(LOCAL_PLAYER, LOCAL_PLAYER, ENDER, STARTED);
        return ripples.live(STARTED).getFirst();
    }

    private static float insetAt(float age) {
        return ViewportRipples.framePulses(viewportRipple(), STARTED + age).getFirst().inset();
    }

    @Nested
    class Starting {

        @Test
        void anAfterimageOfAnotherEntityStartsNoViewportRipple() {
            ViewportRipples ripples = new ViewportRipples();

            ripples.onAfterimage(LOCAL_PLAYER + 1, LOCAL_PLAYER, ENDER, STARTED);

            assertTrue(ripples.live(STARTED).isEmpty());
        }

        @Test
        void anAfterimageOfTheLocalPlayerStartsExactlyOne() {
            ViewportRipples ripples = new ViewportRipples();

            ripples.onAfterimage(LOCAL_PLAYER, LOCAL_PLAYER, ENDER, STARTED);

            assertEquals(1, ripples.live(STARTED).size());
            assertEquals(ENDER, ripples.live(STARTED).getFirst().rgb());
        }

        @Test
        void aRippleIsDroppedOnceItsLastLineHasFaded() {
            ViewportRipples ripples = new ViewportRipples();
            ripples.onAfterimage(LOCAL_PLAYER, LOCAL_PLAYER, ENDER, STARTED);
            long lastFaded = STARTED + (long) (PULSES - 1) * PULSE_GAP_TICKS + LINE_LIFE_TICKS;

            assertEquals(1, ripples.live(lastFaded - 1).size());
            assertTrue(ripples.live(lastFaded).isEmpty());
        }
    }

    @Nested
    class MirrorsTheRipple {

        @Test
        void linesLeaveOnTheRipplesGapsAndFadeAsItsSilhouettesDo() {
            Afterimages.Afterimage<String> silhouettes = new Afterimages.Afterimage<>("player", Vec3.ZERO, ENDER,
                    STARTED, LINE_LIFE_TICKS);
            for (float age = 0f; age < LINE_LIFE_TICKS + PULSES * PULSE_GAP_TICKS; age += 0.5f) {
                List<Afterimages.Pulse> expected = silhouettes.pulses(STARTED + age);
                List<ViewportRipples.FramePulse> frame = ViewportRipples.framePulses(viewportRipple(), STARTED + age);
                assertEquals(expected.size(), frame.size(), "count at age " + age);
                for (int pulse = 0; pulse < frame.size(); pulse++) {
                    assertEquals(expected.get(pulse).alpha(), frame.get(pulse).alpha(), "alpha at age " + age);
                }
            }
        }
    }

    @Nested
    class Motion {

        @Test
        void aLineStartsAtTheScreenEdgeAndMovesInwardAtAnEvenPace() {
            assertEquals(0f, insetAt(0f), EPSILON);
            for (int age = 1; age < LINE_LIFE_TICKS; age++) {
                assertEquals(INSET_PER_TICK, insetAt(age) - insetAt(age - 1f), EPSILON, "uneven pace at " + age);
            }
        }

        @Test
        void aLineStopsAtHalfTheEarlierReach() {
            assertEquals(0.06f, REACH_INSET, EPSILON);
            assertEquals(REACH_INSET, insetAt(LINE_LIFE_TICKS - 0.001f), 0.001f);
        }

        @Test
        void standingLinesKeepAnEvenGapSoTheyReadAsSeparateLines() {
            List<ViewportRipples.FramePulse> frame = ViewportRipples.framePulses(viewportRipple(),
                    STARTED + LINE_LIFE_TICKS - 1f);
            float gap = INSET_PER_TICK * PULSE_GAP_TICKS;

            assertTrue(frame.size() >= 2, "fewer than two lines stand at once");
            for (int line = 1; line < frame.size(); line++) {
                assertEquals(gap, frame.get(line - 1).inset() - frame.get(line).inset(), EPSILON,
                        "lines " + (line - 1) + " and " + line + " are not an even gap apart");
            }
            assertTrue(gap >= 0.015f, "lines stand closer than a sixtieth of the screen");
        }
    }
}
