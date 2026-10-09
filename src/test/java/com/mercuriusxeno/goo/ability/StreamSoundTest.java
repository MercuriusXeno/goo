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

    // decision decay-gnats-degrade-each-block-once: Decay's buzz plays a bee's loop pitched high
    @Test
    void aPitchedSoundStraysAroundItsOwnPitch() {
        StreamSound buzz = new StreamSound(Identifier.withDefaultNamespace("entity.bee.loop"), 4, 0.3f, 0.15f, 1.8f);
        assertEquals(1.65f, buzz.pitchFor(0f), EPSILON);
        assertEquals(1.8f, buzz.pitchFor(0.5f), EPSILON);
        assertEquals(1.95f, buzz.pitchFor(1f), EPSILON);
    }
}
