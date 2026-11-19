package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.crystal.StackHover;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' hold: the lifted blob rests where every other non-centered ability's does.
 */
class KnivesChargeTest {

    private static final long NOW = 400L;
    private static final double NEAR = 1e-6;

    /** How far the hand pass's float math and Mth.sin's lookup table carry a first-person place astray. */
    private static final double THROUGH_THE_HAND_PASS = 1e-4;

    private static void assertNear(Vec3 expected, Vec3 actual, String what) {
        assertNear(expected, actual, what, NEAR);
    }

    private static void assertNear(Vec3 expected, Vec3 actual, String what, double within) {
        assertTrue(expected.distanceTo(actual) < within, what + " should stand at " + expected + ", stands at "
                + actual);
    }

    /**
     * Knives' and Dart's blob cube stands at one place on the server and the
     * client: the cube center StackHover answers for a view is the rest a
     * non-centered blob lifts to in that view at the default field of view,
     * and through a Knives hold the cube stands there, the hand left at its
     * side in the arm pose crystal_knives.json names
     * (decision knives-hand-stays-at-its-side-off-the-screen-center).
     * palm-blob-lifts-as-the-spray-source
     */
    @Nested
    class StandsAtOnePlaceOnTheServerAndTheClient {

        @Test
        void theCubeCenterStackHoverAnswersIsTheRestAStackingBlobLiftsTo() {
            for (boolean gloveRight : new boolean[] {true, false}) {
                assertNear(BlobRest.inView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW, gloveRight,
                                BlobRest.Rest.OVER_THE_PALM),
                        StackHover.cubeCenterInView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW,
                                gloveRight), "the server's cube center, glove right " + gloveRight + ",");
            }
        }

        @Test
        void throughAKnivesHoldTheCubeStandsAtThatRest() {
            ArmPoseKind held = AbilityJson.decode("crystal_knives").pose().holdFrame();
            // decision third-person-blob-stands-above-the-drawn-palm: a third-person caster's cube stands over the
            // drawn palm, so the place the server shares is the caster's own first-person view
            try (SprayingClient client = SprayingClient.local(GooTypes.CRYSTAL);
                 KnivesCharging knives = KnivesCharging.heldTo(0f)) {
                // the server's place is the view's at the default field of view
                client.viewedAt(70f);
                long last = NOW + PalmBlobHop.LIFT_TICKS;
                knives.holdIn(client, held, NOW, last);

                Vec3 place = LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow().place();
                Vec3 rest = BlobRest.inView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW, true,
                        BlobRest.Rest.OVER_THE_PALM);
                assertNear(rest, place, "the cube", THROUGH_THE_HAND_PASS);
                assertNear(StackHover.cubeCenterInView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW,
                        true), place, "the cube", THROUGH_THE_HAND_PASS);
            }
        }
    }
}
