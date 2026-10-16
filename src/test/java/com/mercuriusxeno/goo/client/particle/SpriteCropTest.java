package com.mercuriusxeno.goo.client.particle;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A break particle wears a random quarter of its block's particle sprite, as
 * vanilla's break particles do, inside the sprite's own window of the atlas
 * (decision unmake-drink-is-a-stream-of-break-particles).
 */
class SpriteCropTest {

    private static final float U0 = 0.25f;
    private static final float U1 = 0.3125f;
    private static final float V0 = 0.5f;
    private static final float V1 = 0.5625f;
    private static final float QUARTER = (U1 - U0) / 4;
    private static final float SLACK = 1e-6f;

    @Test
    void theCropIsAQuarterOfTheSpriteLyingInsideIt() {
        for (long seed = 0; seed < 64; seed++) {
            SpriteCrop crop = SpriteCrop.within(U0, U1, V0, V1, RandomSource.create(seed));
            assertTrue(crop.u0() >= U0 - SLACK && crop.u1() <= U1 + SLACK, "u " + crop);
            assertTrue(crop.v0() >= V0 - SLACK && crop.v1() <= V1 + SLACK, "v " + crop);
            assertTrue(Math.abs(crop.u1() - crop.u0() - QUARTER) < SLACK, "width " + crop);
        }
    }

    @Test
    void particlesSeededApartTakeDifferentCrops() {
        assertNotEquals(SpriteCrop.within(U0, U1, V0, V1, RandomSource.create(1)),
                SpriteCrop.within(U0, U1, V0, V1, RandomSource.create(2)));
    }
}
