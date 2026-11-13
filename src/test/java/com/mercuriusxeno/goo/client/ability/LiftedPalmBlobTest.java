package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * While a Goop or Slick spray runs, its caster's palm blob stands lifted at
 * the spray source in the glove's selected goo type, larger than the palm
 * blob at rest and beating between two sizes, for the local player and
 * another player alike; once the stream's ticks stop, the spray reads ended
 * and no lifted blob draws.
 * palm-blob-lifts-as-the-spray-source
 */
class LiftedPalmBlobTest {

    private static final long NOW = 400L;
    private static final float PARTIAL_TICK = 0.5f;
    private static final double DELTA = 1e-9;
    /** Samples taken across one beat. */
    private static final int SAMPLES = 64;

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) <= DELTA, "expected " + expected + ", was " + actual);
    }

    @Nested
    class StandsAtTheSprayingSource {

        @Test
        void theLocalCastersBlobStandsAtTheSourceInItsViewInTheGlovesType() {
            try (SprayingClient client = SprayingClient.local(GooTypes.ZOO)) {
                client.at(NOW);
                LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, NOW);

                LiftedPalmBlob.Blob blob = LiftedPalmBlob.blobOf(SprayingClient.CASTER, PARTIAL_TICK).orElseThrow();

                assertNear(SpraySource.inView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW, true),
                        blob.place());
                assertEquals(GooTypes.ZOO, blob.type());
            }
        }

        @Test
        void anotherPlayersBlobStandsAtTheSourceInThatPlayersViewInTheGlovesType() {
            try (SprayingClient client = SprayingClient.another(GooTypes.FROST)) {
                client.at(NOW);
                LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, NOW);

                LiftedPalmBlob.Blob blob = LiftedPalmBlob.blobOf(SprayingClient.CASTER, PARTIAL_TICK).orElseThrow();

                assertNear(SpraySource.inView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW, true),
                        blob.place());
                assertEquals(GooTypes.FROST, blob.type());
            }
        }
    }

    @Nested
    class BeatsAboveTheRestSize {

        @Test
        void theLiftedSizeBeatsBetweenTwoSizesAboveThePalmBlobsRestAndRepeatsEachBeat() {
            double least = Double.MAX_VALUE;
            double greatest = -Double.MAX_VALUE;
            for (int sample = 0; sample <= SAMPLES; sample++) {
                double time = NOW + LiftedPalmBlob.BEAT_TICKS * sample / SAMPLES;
                float size = LiftedPalmBlob.sizeAt(time);
                least = Math.min(least, size);
                greatest = Math.max(greatest, size);
                assertEquals(size, LiftedPalmBlob.sizeAt(time + LiftedPalmBlob.BEAT_TICKS), 1e-4,
                        "the size a beat later should be the size now");
            }
            assertTrue(least > KnivesCharge.PALM_REST, "the least lifted size " + least
                    + " should be above the palm blob's rest size " + KnivesCharge.PALM_REST);
            assertTrue(greatest > least, "the lifted size should beat, from " + least + " to " + greatest);
        }
    }

    @Nested
    class OneBlobAtATime {

        @Test
        void aStreamTickNowRunsTheSprayAndDrawsTheLiftedBlob() {
            try (SprayingClient client = SprayingClient.another(GooTypes.ZOO)) {
                client.at(NOW);
                LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, NOW);

                assertTrue(LiftedPalmBlob.running(SprayingClient.CASTER, NOW), "the spray should read running");
                assertTrue(LiftedPalmBlob.blobOf(SprayingClient.CASTER, PARTIAL_TICK).isPresent(),
                        "the lifted blob should draw");
            }
        }

        @Test
        void aLastStreamTickOlderThanTheStreamsGapEndsTheSprayAndDrawsNoLiftedBlob() {
            try (SprayingClient client = SprayingClient.another(GooTypes.ZOO)) {
                long ended = NOW + LiftedPalmBlob.STREAM_GAP_TICKS + 1;
                client.at(ended);
                LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, NOW);

                assertFalse(LiftedPalmBlob.running(SprayingClient.CASTER, ended), "the spray should read ended");
                assertEquals(Optional.empty(), LiftedPalmBlob.blobOf(SprayingClient.CASTER, PARTIAL_TICK),
                        "the lifted blob should be gone");
            }
        }

        @Test
        void aStreamTickWithinTheStreamsGapKeepsTheSprayRunning() {
            assertTrue(LiftedPalmBlob.runningAt(NOW, NOW + LiftedPalmBlob.STREAM_GAP_TICKS),
                    "a stream tick landing late within the gap should keep the spray running");
        }
    }
}
