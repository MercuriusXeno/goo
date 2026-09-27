package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that a machine panel fades in and out linearly over 0.1 s on the
 * animator's clock (decision diagnose-then-fix-hud-panel-fade), driven at a
 * fixed 60 fps frame rather than the system clock.
 */
class HudAnimatorTest {

    /** One frame at 60 fps; six of them are the 0.1 s fade. */
    private static final float FRAME = 1f / 60f;
    /** Frames in one full fade. */
    private static final int FADE_FRAMES = 6;
    /** Float slack on a fully faded reading. */
    private static final float EXACT = 1e-6f;

    /**
     * Ticks the animator the given number of frames at one target.
     *
     * @param animator the animator
     * @param target   the target, or null for none
     * @param frames   the frame count
     */
    private static void tickFrames(HudAnimator<String> animator, String target, int frames) {
        for (int i = 0; i < frames; i++) {
            animator.tick(target, FRAME);
        }
    }

    /**
     * An animator that has been aiming at one machine long enough to fade in fully.
     *
     * @return the faded-in animator
     */
    private static HudAnimator<String> fadedIn() {
        HudAnimator<String> animator = new HudAnimator<>(String::equals);
        tickFrames(animator, "vat", FADE_FRAMES);
        return animator;
    }

    @Nested
    class Emerge {

        @Test
        void reachesFullOpacityAtOneTenth() {
            assertEquals(1f, fadedIn().opacity(), EXACT);
        }

        @Test
        void isHalfFadedAtOneTwentieth() {
            HudAnimator<String> animator = new HudAnimator<>(String::equals);
            tickFrames(animator, "vat", FADE_FRAMES / 2);
            assertEquals(0.5f, animator.opacity(), 0.05f);
        }

        @Test
        void movesEveryFrameBeforeFullOpacity() {
            HudAnimator<String> animator = new HudAnimator<>(String::equals);
            tickFrames(animator, "vat", 1);
            assertEquals(1f / FADE_FRAMES, animator.opacity(), 0.01f);
            tickFrames(animator, "vat", 1);
            assertEquals(2f / FADE_FRAMES, animator.opacity(), 0.01f);
        }

        @Test
        void aNewTargetFadesInFromZero() {
            HudAnimator<String> animator = fadedIn();
            tickFrames(animator, "crucible", 1);
            assertEquals(1f / FADE_FRAMES, animator.opacity(), 0.01f);
        }
    }

    @Nested
    class Retract {

        @Test
        void reachesZeroOpacityAndClearsAtOneTenth() {
            HudAnimator<String> animator = fadedIn();
            tickFrames(animator, null, FADE_FRAMES);
            assertEquals(0f, animator.opacity());
            assertNull(animator.tracked());
        }

        @Test
        void staysTrackedWhileFadingOut() {
            HudAnimator<String> animator = fadedIn();
            tickFrames(animator, null, FADE_FRAMES - 1);
            assertEquals(1f / FADE_FRAMES, animator.opacity(), 0.01f);
            assertNotNull(animator.tracked());
        }

        @Test
        void aimingBackFadesInFromWhereTheRetractStood() {
            HudAnimator<String> animator = fadedIn();
            tickFrames(animator, null, FADE_FRAMES / 2);
            tickFrames(animator, "vat", 1);
            assertEquals(4f / FADE_FRAMES, animator.opacity(), 0.01f);
        }
    }
}
