package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The glass field draws one face where two different block types meet and
 * none where the same block carries on: the face of the block farther from
 * the diver, so from inside stone the diver sees the stone's face against a
 * cave and an ore's face against the stone it sits in
 * (decision sculker-shows-blocks-translucent-close-up).
 */
class SculkerGlassRendererTest {

    private static final double NEAR = 2;
    private static final double FAR = 3;

    @Test
    void drawsStonesFaceAgainstACaveFromInsideTheStone() {
        assertTrue(SculkerGlassRenderer.drawsFace(true, false, NEAR, FAR));
    }

    @Test
    void drawsTheFartherBlocksFaceWhereTwoBlocksMeet() {
        assertTrue(SculkerGlassRenderer.drawsFace(false, false, FAR, NEAR));
        assertFalse(SculkerGlassRenderer.drawsFace(false, false, NEAR, FAR));
    }

    @Test
    void drawsNothingWhereTheSameBlockCarriesOn() {
        assertFalse(SculkerGlassRenderer.drawsFace(false, true, FAR, NEAR));
    }
}
