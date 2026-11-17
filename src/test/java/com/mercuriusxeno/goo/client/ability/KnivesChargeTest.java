package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' hold: the lifted blob rests where every other ability's does.
 */
class KnivesChargeTest {

    private static final long NOW = 400L;
    private static final double NEAR = 1e-6;

    /**
     * Through a Knives hold the lifted blob cube rests at the steady point by
     * the glove that Goop and Slick lift theirs to, the hand left at its side
     * in the arm pose crystal_knives.json names
     * (decision knives-hand-stays-at-its-side-off-the-screen-center).
     */
    @Nested
    class RestsAtTheSteadyPoint {

        @Test
        void theCubeRestsWhereBlobRestPutsTheLiftThroughAKnivesHold() {
            ArmPoseKind held = AbilityJson.decode("crystal_knives").pose().holdFrame();
            try (SprayingClient client = SprayingClient.another(GooTypes.CRYSTAL);
                 KnivesCharging knives = KnivesCharging.heldTo(0f)) {
                long last = NOW + PalmBlobHop.LIFT_TICKS;
                knives.holdIn(client, held, NOW, last);

                Vec3 place = LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow().place();
                Vec3 rest = BlobRest.of(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, 0f);
                assertTrue(place.distanceTo(rest) < NEAR, "the cube should rest at " + rest + ", rests at " + place);
            }
        }
    }
}
