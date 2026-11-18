package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.ability.Charge;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Knives' hold: a chime at each stage of knives formed, never past the
 * stages the charge names, the glove's palm blob swelling with the charge,
 * and the blob held afloat through the wind-up alone (decisions
 * knives-form-in-fibonacci-stages-to-twelve, charge-swells-the-palm-blob,
 * knives-blob-floats-ahead-of-the-hand-and-spews-the-knives).
 */
class KnivesChargeTest {

    private static final Charge KNIVES = KnivesCharging.KNIVES;
    private static final float TOLERANCE = 1e-5f;

    @Nested
    class ChimeSteps {

        @Test
        void eachStageOfTwelveTicksChimesOnceUpToTheStagesTheChargeNames() {
            assertEquals(0, KnivesCharge.stagesFormedAt(KNIVES, 11));
            assertEquals(1, KnivesCharge.stagesFormedAt(KNIVES, 12));
            assertEquals(4, KnivesCharge.stagesFormedAt(KNIVES, 59));
            assertEquals(5, KnivesCharge.stagesFormedAt(KNIVES, 60));
            assertEquals(5, KnivesCharge.stagesFormedAt(KNIVES, 600));
        }
    }

    @Nested
    class PalmBlobScale {

        @Test
        void thePalmBlobRestsAtItsOwnSizeWithNoCharge() {
            assertEquals(KnivesCharge.PALM_REST, KnivesCharge.palmScaleAt(0f), TOLERANCE);
        }

        @Test
        void thePalmBlobSitsMidwayAtHalfACharge() {
            assertEquals((KnivesCharge.PALM_REST + KnivesCharge.PALM_FULLEST) / 2f,
                    KnivesCharge.palmScaleAt(0.5f), TOLERANCE);
        }

        @Test
        void thePalmBlobSwellsToItsFullestAtFullChargeAndNoFurther() {
            assertEquals(KnivesCharge.PALM_FULLEST, KnivesCharge.palmScaleAt(1f), TOLERANCE);
            assertEquals(KnivesCharge.PALM_FULLEST, KnivesCharge.palmScaleAt(2f), TOLERANCE);
        }
    }

    @Nested
    class FloatsTheBlob {

        @Test
        void theWindUpFloatsTheBlob() {
            assertTrue(KnivesCharge.floatsAt(new AbilityPoses.Posed(ArmPoseKind.WIND_UP, 0, true, 0f)));
        }

        @Test
        void aDroppedArmFloatsTheBlobNoLonger() {
            assertFalse(KnivesCharge.floatsAt(new AbilityPoses.Posed(ArmPoseKind.NONE, 0, true, 0f)));
        }

        @Test
        void aWindUpLetGoFloatsTheBlobNoLonger() {
            assertFalse(KnivesCharge.floatsAt(new AbilityPoses.Posed(ArmPoseKind.WIND_UP, 0, true, 0.5f, true)));
        }
    }

    @Nested
    class PalmShare {

        @Test
        void noKnifeFormingChargeHeldShowsNoShare() {
            assertEquals(0f, KnivesCharge.palmShareOf(null, true, 0.7f), TOLERANCE);
        }

        @Test
        void anArmNotWoundUpShowsNoShare() {
            assertEquals(0f, KnivesCharge.palmShareOf(KNIVES, false, 0.7f), TOLERANCE);
        }

        @Test
        void aWoundUpKnifeFormingChargeShowsTheShareItReached() {
            assertEquals(0.7f, KnivesCharge.palmShareOf(KNIVES, true, 0.7f), TOLERANCE);
            assertEquals(1f, KnivesCharge.palmShareOf(KNIVES, true, 1.5f), TOLERANCE);
        }
    }

    // goo-sound-at-the-listener-reaches-both-ears: the chimes ring at the local caster's own eye
    @Nested
    class ChimesAtTheCastersEye {

        private final Vec3 eye = new Vec3(12.3, 65.62, -7.9);

        @Test
        void aStagesChimePlaysAtTheListenerUnattenuated() {
            SimpleSoundInstance chime = KnivesCharge.chimeOf(eye, 0.5f);
            assertTrue(chime.isRelative());
            assertEquals(SoundInstance.Attenuation.NONE, chime.getAttenuation());
        }

        @Test
        void theLastStagesResonancePlaysAtTheListenerUnattenuated() {
            SimpleSoundInstance resonance = KnivesCharge.resonanceOf(eye);
            assertTrue(resonance.isRelative());
            assertEquals(SoundInstance.Attenuation.NONE,
                    resonance.getAttenuation());
        }
    }
}
