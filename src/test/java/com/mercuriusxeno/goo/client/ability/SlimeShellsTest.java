package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static com.mercuriusxeno.goo.client.ability.SlimeShells.HOLD_TICKS;
import static com.mercuriusxeno.goo.client.ability.SlimeShells.LIFE_TICKS;
import static com.mercuriusxeno.goo.client.ability.SlimeShells.MELT_TICKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the slime shell a charm-like cast's dart leaves on its mob: it
 * stands whole for its hold, melts from the head to the feet, and a burst
 * takes it off at once; the shell draws as the body puffed out along its
 * surface, shown below the melt line and gone above it
 * (decision charm-like-abilities-lock-the-highlighted-mob-on-right-click).
 */
class SlimeShellsTest {

    private static final int MOB = 7;
    private static final long LANDED = 1000;
    private static final float EPSILON = 1e-5f;

    private static SlimeShells envelopedAtLanding() {
        SlimeShells shells = new SlimeShells();
        shells.envelop(MOB, GooTypes.HEX, LANDED);
        return shells;
    }

    @Nested
    class Life {

        @Test
        void standsWholeThroughItsHold() {
            SlimeShells shells = envelopedAtLanding();
            assertEquals(Optional.of(0f), shells.shellOf(MOB, LANDED + HOLD_TICKS).map(SlimeShells.Worn::melted));
        }

        @Test
        void meltsHalfwayDownAtTheMiddleOfItsMelt() {
            SlimeShells shells = envelopedAtLanding();
            float melted = shells.shellOf(MOB, LANDED + HOLD_TICKS + MELT_TICKS / 2f).orElseThrow().melted();
            assertEquals(0.5f, melted, EPSILON);
        }

        @Test
        void isGoneOnceMelted() {
            SlimeShells shells = envelopedAtLanding();
            assertTrue(shells.shellOf(MOB, LANDED + LIFE_TICKS).isEmpty());
        }

        @Test
        void aBurstTakesItOffAtOnceAndAnswersItsGoo() {
            SlimeShells shells = envelopedAtLanding();
            assertEquals(Optional.of(GooTypes.HEX), shells.burst(MOB));
            assertTrue(shells.shellOf(MOB, LANDED + 1).isEmpty());
        }
    }

    @Nested
    class Drawing {

        @Test
        void showsFullBelowTheMeltLineAndNothingAboveIt() {
            float melted = 0.5f;
            assertEquals(0f, ShellVertices.shownAt(0.2f, melted));
            assertEquals(1f, ShellVertices.shownAt(0.9f, melted));
        }

        @Test
        void standsOffTheSkinAlongItsNormal() {
            Vector3f puffed = ShellVertices.puffedOut(1f, 2f, 3f, 0f, 1f, 0f);
            assertEquals(new Vector3f(1f, 2f + ShellVertices.PUFF_BLOCKS, 3f), puffed);
        }
    }
}
