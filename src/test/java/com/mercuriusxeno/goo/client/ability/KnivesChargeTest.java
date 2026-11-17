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

    private static void assertNear(Vec3 expected, Vec3 actual, String what) {
        assertTrue(expected.distanceTo(actual) < NEAR, what + " should stand at " + expected + ", stands at "
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
        void theCubeCenterStackHoverAnswersIsTheRestANonCenteredBlobLiftsTo() {
            for (boolean gloveRight : new boolean[] {true, false}) {
                assertNear(BlobRest.inView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW, gloveRight,
                                BlobRest.Rest.ABOVE_THE_HAND),
                        StackHover.cubeCenterInView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW,
                                gloveRight), "the server's cube center, glove right " + gloveRight + ",");
            }
        }

        @Test
        void throughAKnivesHoldTheCubeStandsAtThatRest() {
            ArmPoseKind held = AbilityJson.decode("crystal_knives").pose().holdFrame();
            try (SprayingClient client = SprayingClient.another(GooTypes.CRYSTAL);
                 KnivesCharging knives = KnivesCharging.heldTo(0f)) {
                long last = NOW + PalmBlobHop.LIFT_TICKS;
                knives.holdIn(client, held, NOW, last);

                Vec3 place = LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow().place();
                Vec3 rest = BlobRest.inView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW, true,
                        BlobRest.Rest.ABOVE_THE_HAND);
                assertNear(rest, place, "the cube");
                assertNear(StackHover.cubeCenterInView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW,
                        true), place, "the cube");
            }
        }
    }
}
