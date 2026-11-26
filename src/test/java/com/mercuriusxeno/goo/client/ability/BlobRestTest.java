package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the lifted palm blob rests: on the center line below the crosshair,
 * clear of it, 0.7 ahead of the eye and 0.15 below the look, for an ability
 * tagged centered, and for every other ability but Bore at the spray source
 * Goop and Slick drip from, above the hand.
 * centered-abilities-float-the-blob-below-the-crosshair
 * palm-blob-lifts-as-the-spray-source
 */
class BlobRestTest {

    private static final Vec3 EYE = new Vec3(3, 65.62, -2);
    private static final float PITCH = 30f;
    private static final float YAW = 20f;
    /** Vec3.directionFromRotation reads Mth.sin's lookup table, which strays this far. */
    private static final double SINE_TABLE = 1e-3;
    private static final float QUARTER_TURN = 90f;
    /** How far ahead of the eye the centered blob rests, along the look. */
    private static final double CENTER_AHEAD = 0.7;
    /** How far below the look the centered blob rests. */
    private static final double CENTER_BELOW = 0.15;
    /** The lifted blob's greatest half height in the world, which the crosshair must clear. */
    private static final double LIFTED_HALF_HEIGHT = GooFlightRenderer.SHELL_HW
            * LiftedPalmBlob.FLIGHT_SIZE_OF_PALM_REST * LiftedPalmBlob.LIFTED_GREATEST;

    private static Vec3 right() {
        return Vec3.directionFromRotation(0f, YAW + QUARTER_TURN);
    }

    private static Vec3 up() {
        return Vec3.directionFromRotation(PITCH - QUARTER_TURN, YAW);
    }

    private static Vec3 ahead() {
        return Vec3.directionFromRotation(PITCH, YAW);
    }

    @Test
    void aCenteredAbilitysBlobRestsOnTheCenterLineAheadOfTheEyeAndBelowTheLook() {
        for (boolean gloveRight : new boolean[] {true, false}) {
            Vec3 rest = BlobRest.inView(EYE, PITCH, YAW, gloveRight, BlobRest.Rest.CENTER_LINE).subtract(EYE);

            assertEquals(0, rest.dot(right()), SINE_TABLE, "the centered blob should stand on the center line");
            assertEquals(CENTER_AHEAD, rest.dot(ahead()), SINE_TABLE, "the centered blob should stand 0.7 ahead");
            assertEquals(-CENTER_BELOW, rest.dot(up()), SINE_TABLE, "the centered blob should stand 0.15 below");
        }
    }

    @Test
    void aCenteredAbilitysBlobSettlesBelowTheCrosshairClearOfIt() {
        Vec3 rest = BlobRest.inView(EYE, PITCH, YAW, true, BlobRest.Rest.CENTER_LINE).subtract(EYE);

        assertTrue(rest.dot(up()) + LIFTED_HALF_HEIGHT < 0, "the centered blob's top, " + rest.dot(up())
                + " plus " + LIFTED_HALF_HEIGHT + " off the look, should stand below the crosshair");
    }

    @Test
    void anAbilityWithNoSyncedCopyLiftsItsBlobAboveTheHand() {
        assertEquals(BlobRest.Rest.ABOVE_THE_HAND, BlobRest.Rest.of(null));
    }

    @Test
    void aBlobLiftedAboveTheHandRestsAtTheSpraySource() {
        assertEquals(SpraySource.inView(EYE, PITCH, YAW, true),
                BlobRest.inView(EYE, PITCH, YAW, true, BlobRest.Rest.ABOVE_THE_HAND));
    }

    // knives-hover-above-the-blob-cube-over-the-hand: the knife pile's cube stands one cube edge over the palm
    @Test
    void aKnivesBlobRestsOneCubeEdgeOverThePalm() {
        com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility knives =
                org.mockito.Mockito.mock(com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility.class);
        org.mockito.Mockito.when(knives.delivery())
                .thenReturn(com.mercuriusxeno.goo.ability.AbilityJson.decode("crystal_knives").delivery());
        assertEquals(BlobRest.Rest.OVER_THE_PALM, BlobRest.Rest.of(knives));

        double overTheHand = BlobRest.inView(EYE, PITCH, YAW, true, BlobRest.Rest.ABOVE_THE_HAND).subtract(EYE).dot(up());
        double overThePalm = BlobRest.inView(EYE, PITCH, YAW, true, BlobRest.Rest.OVER_THE_PALM).subtract(EYE).dot(up());
        assertEquals(SpraySource.RISE_ABOVE_THE_PALM - 2 * 0.15 * 0.5 * 0.9, overTheHand - overThePalm, SINE_TABLE,
                "the knives' cube should stand one cube edge over the palm, the spray source's rise less");
    }

    /** A drawn palm held out to the right of a body tilted off the world's up. */
    static final DrawnPalm.Palm DRAWN = new DrawnPalm.Palm(new Vec3(3.4, 64.9, -1.5), new Vec3(0, 1, 0),
            new Vec3(0.2, 0.96, -0.1).normalize());

    // decision third-person-blob-stands-above-the-drawn-palm: every rest kind, the centered ones too
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(BlobRest.Rest.class)
    void aThirdPersonCastersRestStandsOnTheModelsUpLineAboveTheDrawnPalm(BlobRest.Rest rest) {
        Vec3 off = BlobRest.aboveTheDrawnPalm(DRAWN, rest).subtract(DRAWN.place());

        assertTrue(off.dot(DRAWN.modelUp()) > 0, "the rest should stand above the palm, off it by " + off);
        assertEquals(0, off.cross(DRAWN.modelUp()).length(), 1e-9, "the rest should stand on the model's up "
                + "line through the palm, off it by " + off);
    }

    @Test
    void aCasterDrawnInThirdPersonRestsAboveTheirDrawnPalm() {
        try (SprayingClient client = SprayingClient.another(com.mercuriusxeno.goo.type.GooTypes.ZOMBIE)) {
            client.drawsThePalm(DRAWN);

            Vec3 rest = BlobRest.of(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, 0f);

            Vec3 expected = BlobRest.aboveTheDrawnPalm(DRAWN, BlobRest.Rest.ABOVE_THE_HAND);
            assertTrue(expected.distanceTo(rest) < 1e-9, "the rest should stand at " + expected + ", was " + rest);
        }
    }

    // decision third-person-blob-stands-above-the-drawn-palm: under Invert the model's up points down
    @Test
    void aCasterDrawnUpsideDownRestsBelowTheirDrawnPalmTowardTheHead() {
        DrawnPalm.Palm hanging = new DrawnPalm.Palm(DRAWN.place(), new Vec3(0, -1, 0), new Vec3(0, -1, 0));
        try (SprayingClient client = SprayingClient.another(com.mercuriusxeno.goo.type.GooTypes.ZOMBIE)) {
            client.drawsThePalm(hanging);

            Vec3 rest = BlobRest.of(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, 0f);

            assertTrue(new Vec3(DRAWN.place().x, DRAWN.place().y - SpraySource.RISE_ABOVE_THE_PALM,
                    DRAWN.place().z).distanceTo(rest) < 1e-9, "the rest " + rest + " should stand a spray "
                    + "source's rise below the palm " + DRAWN.place());
        }
    }

    // dart-javelins-hover-over-the-shrinking-blob-cube: Dart's cube rests as low as Knives'
    @Test
    void dartsBlobRestsOverThePalmAsKnivesDoes() {
        com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility dart =
                org.mockito.Mockito.mock(com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility.class);
        org.mockito.Mockito.when(dart.delivery())
                .thenReturn(com.mercuriusxeno.goo.ability.AbilityJson.decode("metal_dart").delivery());
        assertEquals(BlobRest.Rest.OVER_THE_PALM, BlobRest.Rest.of(dart));
    }}
