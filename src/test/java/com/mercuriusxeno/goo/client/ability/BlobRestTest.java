package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the lifted palm blob rests: on the center line below the crosshair,
 * clear of it, for an ability marked centered, and at the steady point by
 * the glove that Goop and Slick use for every other ability.
 * centered-abilities-float-the-blob-below-the-crosshair
 */
class BlobRestTest {

    private static final Vec3 EYE = new Vec3(3, 65.62, -2);
    private static final float PITCH = 30f;
    private static final float YAW = 20f;
    /** Vec3.directionFromRotation reads Mth.sin's lookup table, which strays this far. */
    private static final double SINE_TABLE = 1e-3;
    private static final float QUARTER_TURN = 90f;
    /** The lifted blob's greatest half height in the world, which the crosshair must clear. */
    private static final double LIFTED_HALF_HEIGHT = GooFlightRenderer.SHELL_HW
            * LiftedPalmBlob.FLIGHT_SIZE_OF_PALM_REST * LiftedPalmBlob.LIFTED_GREATEST;

    private static Vec3 right() {
        return Vec3.directionFromRotation(0f, YAW + QUARTER_TURN);
    }

    private static Vec3 up() {
        return Vec3.directionFromRotation(PITCH - QUARTER_TURN, YAW);
    }

    @Test
    void aCenteredAbilitysBlobRestsCenteredAcrossTheView() {
        for (boolean gloveRight : new boolean[] {true, false}) {
            Vec3 rest = BlobRest.inView(EYE, PITCH, YAW, gloveRight, true).subtract(EYE);

            assertEquals(0, rest.dot(right()), SINE_TABLE, "the centered blob should stand on the center line");
        }
    }

    @Test
    void aCenteredAbilitysBlobSettlesBelowTheCrosshairClearOfIt() {
        Vec3 rest = BlobRest.inView(EYE, PITCH, YAW, true, true).subtract(EYE);

        assertTrue(rest.dot(up()) + LIFTED_HALF_HEIGHT < 0, "the centered blob's top, " + rest.dot(up())
                + " plus " + LIFTED_HALF_HEIGHT + " off the look, should stand below the crosshair");
        assertTrue(rest.dot(Vec3.directionFromRotation(PITCH, YAW)) > 0, "the centered blob should stand ahead");
    }

    @Test
    void anUnmarkedAbilitysBlobRestsAtTheSteadyPointByTheGlove() {
        assertEquals(SpraySource.inView(EYE, PITCH, YAW, true), BlobRest.inView(EYE, PITCH, YAW, true, false));
    }
}
