package com.mercuriusxeno.goo.ability;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stream's sound plays on the hold's first tick and every so many ticks
 * after, each play's pitch strayed within its spread either way
 * (decision mycosis-spore-stream-buds-and-poisons).
 */
class StreamSoundTest {

    private static final float EPSILON = 1e-6f;
    private static final StreamSound BUBBLES =
            new StreamSound(Identifier.withDefaultNamespace("block.bubble_column.bubble_pop"), 2, 0.4f, 0.2f);

    @Test
    void itPlaysOnTheFirstTickAndEveryOtherAfter() {
        assertTrue(BUBBLES.playsOn(1));
        assertFalse(BUBBLES.playsOn(2));
        assertTrue(BUBBLES.playsOn(3));
        assertFalse(BUBBLES.playsOn(4));
    }

    @Test
    void itsPitchStraysWithinTheSpreadEitherWay() {
        assertEquals(0.8f, BUBBLES.pitchFor(0f), EPSILON);
        assertEquals(1f, BUBBLES.pitchFor(0.5f), EPSILON);
        assertEquals(1.2f, BUBBLES.pitchFor(1f), EPSILON);
    }
}
