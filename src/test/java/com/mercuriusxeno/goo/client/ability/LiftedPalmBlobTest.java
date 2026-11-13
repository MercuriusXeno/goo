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
 * While a Goop or Slick spray runs, its caster's palm blob lifts to the
 * spray source in the glove's selected goo type, larger than the palm blob at
 * rest and beating between two sizes, for the local player and another
 * player alike. As the spray begins the blob eases off the palm to the
 * source, swelling as it goes; once the stream's ticks stop it eases back and
 * shrinks to rest size, and a spray pressed again while it settles turns it
 * round from where it stands.
 * palm-blob-lifts-as-the-spray-source
 */
class LiftedPalmBlobTest {

    private static final long NOW = 400L;
    private static final float PARTIAL_TICK = 0.5f;
    private static final double DELTA = 1e-6;
    /** Samples taken across one beat. */
    private static final int SAMPLES = 64;
    private static final Vec3 PALM = new Vec3(3.35, 64.9, -1.6);
    private static final Vec3 SOURCE = new Vec3(3.2, 65.2, -1.0);
    /** A stream tick every game tick from the spray's start until the blob has reached the source. */
    private static final long EASED = NOW + (long) Math.ceil(LiftedPalmBlob.EASE_TICKS);

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) <= DELTA, "expected " + expected + ", was " + actual);
    }

    /** A lift whose stream ticked every game tick from its start through its last tick. */
    private static LiftedPalmBlob.Lift sprayedThrough(long first, long last) {
        LiftedPalmBlob.Lift lift = LiftedPalmBlob.Lift.beginning(first, 0f, SprayingClient.SENT_GLOVE);
        for (long tick = first + 1; tick <= last; tick++) {
            lift = lift.sprayedAt(tick, SprayingClient.SENT_GLOVE);
        }
        return lift;
    }

    /** The game time a lift's settle ends at, the blob back on the palm. */
    private static double settledAt(long lastStreamTick) {
        return lastStreamTick + LiftedPalmBlob.STREAM_GAP_TICKS + 1 + LiftedPalmBlob.EASE_TICKS;
    }

    private static void sprayEveryTickThroughTheEase() {
        for (long tick = NOW; tick <= EASED; tick++) {
            LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, tick);
        }
    }

    @Nested
    class StandsAtTheSprayingSource {

        @Test
        void theLocalCastersBlobStandsAtTheSourceInItsViewInTheGlovesType() {
            try (SprayingClient client = SprayingClient.local(GooTypes.ZOO)) {
                client.at(EASED);
                sprayEveryTickThroughTheEase();

                LiftedPalmBlob.Blob blob = LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow();

                assertNear(SpraySource.inView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW, true),
                        blob.place());
                assertEquals(GooTypes.ZOO, blob.type());
            }
        }

        @Test
        void anotherPlayersBlobStandsAtTheSourceInThatPlayersViewInTheGlovesType() {
            try (SprayingClient client = SprayingClient.another(GooTypes.FROST)) {
                client.at(EASED);
                sprayEveryTickThroughTheEase();

                LiftedPalmBlob.Blob blob = LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow();

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
                float size = LiftedPalmBlob.liftedSizeAt(time);
                least = Math.min(least, size);
                greatest = Math.max(greatest, size);
                assertEquals(size, LiftedPalmBlob.liftedSizeAt(time + LiftedPalmBlob.BEAT_TICKS), 1e-4,
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
        void aLastStreamTickOlderThanTheStreamsGapAndTheSettleEndsTheSprayAndDrawsNoLiftedBlob() {
            try (SprayingClient client = SprayingClient.another(GooTypes.ZOO)) {
                long ended = (long) Math.ceil(settledAt(NOW));
                client.at(ended);
                LiftedPalmBlob.sprayed(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, NOW);

                assertFalse(LiftedPalmBlob.running(SprayingClient.CASTER, ended), "the spray should read ended");
                assertEquals(Optional.empty(), LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f),
                        "the lifted blob should be gone");
            }
        }

        @Test
        void aStreamTickWithinTheStreamsGapKeepsTheSprayRunning() {
            assertTrue(LiftedPalmBlob.runningAt(NOW, NOW + LiftedPalmBlob.STREAM_GAP_TICKS),
                    "a stream tick landing late within the gap should keep the spray running");
        }
    }

    @Nested
    class EasesOffThePalm {

        @Test
        void atTheSpraysFirstTickTheBlobStandsOnThePalmAtRestSize() {
            float share = LiftedPalmBlob.Lift.beginning(NOW, 0f, SprayingClient.SENT_GLOVE).shareAt(NOW);

            assertNear(PALM, LiftedPalmBlob.placeAt(share, PALM, SOURCE));
            assertEquals(KnivesCharge.PALM_REST, LiftedPalmBlob.sizeAt(share, NOW), DELTA);
        }

        @Test
        void partWayThroughTheEaseTheBlobStandsBetweenThePalmAndTheSourceAndBetweenItsSizes() {
            double partWay = NOW + LiftedPalmBlob.EASE_TICKS / 2;
            float share = sprayedThrough(NOW, EASED).shareAt(partWay);

            assertStrictlyBetween(LiftedPalmBlob.placeAt(share, PALM, SOURCE), PALM, SOURCE);
            float size = LiftedPalmBlob.sizeAt(share, partWay);
            assertTrue(size > KnivesCharge.PALM_REST && size < LiftedPalmBlob.liftedSizeAt(partWay),
                    "part way through the ease the size " + size + " should lie between rest and lifted");
        }

        @Test
        void atTheEasesEndTheBlobStandsAtTheSourceAtTheLiftedSize() {
            double easeEnd = NOW + LiftedPalmBlob.EASE_TICKS;
            float share = sprayedThrough(NOW, EASED).shareAt(easeEnd);

            assertNear(SOURCE, LiftedPalmBlob.placeAt(share, PALM, SOURCE));
            assertEquals(LiftedPalmBlob.liftedSizeAt(easeEnd), LiftedPalmBlob.sizeAt(share, easeEnd), DELTA);
        }
    }

    @Nested
    class SettlesBackOntoThePalm {

        @Test
        void partWayThroughTheSettleTheBlobStandsBetweenTheSourceAndThePalm() {
            double partWay = settledAt(EASED) - LiftedPalmBlob.EASE_TICKS / 2;
            float share = sprayedThrough(NOW, EASED).shareAt(partWay);

            assertStrictlyBetween(LiftedPalmBlob.placeAt(share, PALM, SOURCE), SOURCE, PALM);
        }

        @Test
        void atTheSettlesEndTheBlobStandsOnThePalmAtRestSize() {
            double settled = settledAt(EASED);
            float share = sprayedThrough(NOW, EASED).shareAt(settled);

            assertNear(PALM, LiftedPalmBlob.placeAt(share, PALM, SOURCE));
            assertEquals(KnivesCharge.PALM_REST, LiftedPalmBlob.sizeAt(share, settled), DELTA);
        }

        @Test
        void aSprayPressedAgainWhileTheBlobSettlesTurnsItRoundFromWhereItStands() {
            LiftedPalmBlob.Lift settling = sprayedThrough(NOW, EASED);
            long pressedAgain = (long) (settledAt(EASED) - LiftedPalmBlob.EASE_TICKS / 2);
            float reached = settling.shareAt(pressedAgain);

            LiftedPalmBlob.Lift turned = settling.sprayedAt(pressedAgain, SprayingClient.SENT_GLOVE);

            assertTrue(reached > 0f && reached < 1f, "the settle should be part way, at " + reached);
            assertEquals(reached, turned.shareAt(pressedAgain), DELTA,
                    "the blob should turn round from the share the settle reached");
            assertTrue(turned.sprayedAt(pressedAgain + 1, SprayingClient.SENT_GLOVE).shareAt(pressedAgain + 1)
                    > reached, "a tick later the blob should be lifting again");
        }
    }

    /** Asserts a point lies on the segment between two ends, short of both. */
    private static void assertStrictlyBetween(Vec3 point, Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        double along = point.subtract(from).dot(line) / line.lengthSqr();
        assertTrue(along > DELTA && along < 1 - DELTA, point + " should lie strictly between " + from + " and "
                + to + ", " + along + " of the way");
        assertNear(from.add(line.scale(along)), point);
    }
}
