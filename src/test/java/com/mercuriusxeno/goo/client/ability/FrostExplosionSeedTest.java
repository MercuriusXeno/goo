package com.mercuriusxeno.goo.client.ability;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A frost ring's seed rides to its shader in the vertex normal and reads
 * back as the same angle, and rings fixed by different values seed apart.
 */
class FrostExplosionSeedTest {

    /** The normal rides to the shader in a byte, and the angle off Minecraft's sine table; a thousandth is plenty. */
    private static final double EPSILON = 1e-3;

    @Test
    void theSeedReadsBackFromItsNormal() {
        for (float seed : new float[] {0.3f, 1.7f, 2.9f, -2.2f}) {
            Vector3f normal = FrostExplosionVisual.seedNormal(seed);
            assertEquals(1, normal.length(), EPSILON);
            assertEquals(seed, Math.atan2(normal.z(), normal.x()), EPSILON);
        }
    }

    @Test
    void ringsFixedByDifferentValuesSeedApart() {
        assertNotEquals(FrostExplosionVisual.seedOf(1L), FrostExplosionVisual.seedOf(2L));
    }

    @Test
    void aSeedIsAnAngle() {
        for (long value = 0; value < 50; value++) {
            float seed = FrostExplosionVisual.seedOf(value * 7919L);
            assertTrue(seed >= 0 && seed < 2 * Math.PI);
        }
    }
}
