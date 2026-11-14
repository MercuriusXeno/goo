package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.client.throwing.BlobShape;
import com.mercuriusxeno.goo.client.throwing.GloveInputGate;
import com.mercuriusxeno.goo.network.PalmBlobLiftPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.Optional;
import java.util.stream.Stream;
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
 * round from where it stands. The blob rests pressed on the palm and stands
 * into the cube at one volume, the same shape in both passes, and every lift
 * from the palm begins with a hop. Any held press lifts the blob, a stream,
 * a channel or a charge, and another caster's held ticks lift theirs.
 * palm-blob-lifts-as-the-spray-source
 * every-ability-casts-from-the-lifted-palm-blob
 * palm-blob-rests-pressed-and-stands-into-the-cube
 * every-lift-begins-with-a-hop
 */
class LiftedPalmBlobTest {

    private static final long NOW = 400L;
    private static final float PARTIAL_TICK = 0.5f;
    private static final double DELTA = 1e-6;
    /** How far a float product of three shares may stray from the volume it holds. */
    private static final float VOLUME_TOLERANCE = 1e-5f;
    /** How far Mth.sin's lookup table strays, which Vec3.directionFromRotation reads. */
    private static final double SINE_TABLE = 1e-3;
    /** Samples taken across one beat. */
    private static final int SAMPLES = 64;
    private static final Vec3 PALM = new Vec3(3.35, 64.9, -1.6);
    private static final Vec3 SOURCE = new Vec3(3.2, 65.2, -1.0);
    /** A stream tick every game tick from the spray's start until the blob has reached the source. */
    private static final long EASED = NOW + PalmBlobHop.LIFT_TICKS;
    /** The palm blob at rest: pressed, at rest size. */
    private static final BlobShape REST = PalmBlobHop.pressed().scaled(KnivesCharge.PALM_REST);

    private static void assertNear(Vec3 expected, Vec3 actual) {
        assertTrue(expected.distanceTo(actual) <= DELTA, "expected " + expected + ", was " + actual);
    }

    /** A lift whose stream ticked every game tick from its start through its last tick. */
    private static LiftedPalmBlob.Lift sprayedThrough(long first, long last) {
        LiftedPalmBlob.Lift lift = LiftedPalmBlob.Lift.beginning(first, SprayingClient.SENT_GLOVE);
        for (long tick = first + 1; tick <= last; tick++) {
            lift = lift.sprayedAt(tick, SprayingClient.SENT_GLOVE);
        }
        return lift;
    }

    /** The game time a lift's spray reads ended at, a tick past the stream's gap, and its settle begins. */
    private static double endedAt(long lastStreamTick) {
        return lastStreamTick + LiftedPalmBlob.STREAM_GAP_TICKS + 1;
    }

    /** The game time a lift's settle ends at, the blob back on the palm. */
    private static double settledAt(long lastStreamTick) {
        return endedAt(lastStreamTick) + PalmBlobHop.SETTLE_TICKS;
    }

    /** Part way down the settle's descent, the blob between the source and the palm. */
    private static double partWayDown(long lastStreamTick) {
        return endedAt(lastStreamTick) + PalmBlobHop.EASE_TICKS / 2;
    }

    private static void sprayEveryTickThroughTheEase() {
        for (long tick = NOW; tick <= EASED; tick++) {
            LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, tick);
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
    class DrawsOverTheGloveAndTurnsWithIt {

        @Test
        void theLocalFirstPersonCastersBlobDrawsInTheHandsPassRatherThanTheLevel() {
            try (SprayingClient client = SprayingClient.local(GooTypes.ZOO)) {
                assertFalse(LiftedPalmBlob.drawsInTheLevel(SprayingClient.CASTER),
                        "the hand's pass should draw the local first-person blob, over the glove");
            }
        }

        @Test
        void anotherPlayersBlobDrawsInTheLevel() {
            try (SprayingClient client = SprayingClient.another(GooTypes.ZOO)) {
                assertTrue(LiftedPalmBlob.drawsInTheLevel(SprayingClient.CASTER));
            }
        }

        @Test
        void anotherPlayersBlobTurnsItsFaceAlongThatPlayersLook() {
            Vector3f face = new Vector3f(0, 0, 1).rotate(LiftedPalmBlob.facingTheLook(SprayingClient.PITCH,
                    SprayingClient.YAW));

            Vec3 look = Vec3.directionFromRotation(SprayingClient.PITCH, SprayingClient.YAW);
            assertTrue(look.distanceTo(new Vec3(face)) <= SINE_TABLE, "the blob's face " + face + " should lie "
                    + "along the look " + look);
        }
    }

    @Nested
    class BeatsBelowThePancakesSize {

        /** operator ruling 2026-11-13: the pancake can be spread out and then it shrinks in cuboid form */
        @Test
        void theLiftedSizeBeatsBetweenTwoSizesBelowThePalmBlobsRestAndRepeatsEachBeat() {
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
            assertTrue(greatest < KnivesCharge.PALM_REST, "the greatest lifted size " + greatest
                    + " should be below the palm blob's rest size " + KnivesCharge.PALM_REST);
            assertTrue(greatest > least, "the lifted size should beat, from " + least + " to " + greatest);
        }
    }

    @Nested
    class OneBlobAtATime {

        @Test
        void aStreamTickNowRunsTheSprayAndDrawsTheLiftedBlob() {
            try (SprayingClient client = SprayingClient.another(GooTypes.ZOO)) {
                client.at(NOW);
                LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, NOW);

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
                LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, NOW);

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

    /**
     * A press the gate drives: a stream, a channel run on the player, or a
     * release ability that previews while held, each answering the held route
     * of the delivery and badge it was handed.
     */
    private record HeldPress(Delivery delivery, AbilityBadge badge) implements GloveInputGate.PressActions {

        @Override
        public boolean sendThrow(int heldTicks) {
            return true;
        }

        @Override
        public void holdPose() {
            // the lift reads the press, not the arm
        }

        @Override
        public void releasePose() {
            // the lift reads the press, not the arm
        }

        @Override
        public void dropPose() {
            // the lift reads the press, not the arm
        }

        @Override
        public void hold() {
            // the lift reads the press, not the stream's sends
        }

        @Override
        public boolean runsWhileHeld() {
            return GloveInputGate.runsFromPress(delivery, badge);
        }
    }

    private static GloveInputGate heldThroughTicks(HeldPress actions) {
        GloveInputGate press = new GloveInputGate();
        press.arm();
        for (int tick = 0; tick < PalmBlobHop.LIFT_TICKS; tick++) {
            press.tick(true, actions);
        }
        return press;
    }

    @Nested
    class LiftsWhileAnyAbilityIsHeld {

        @Test
        void aStreamingPressLiftsTheBlob() {
            assertTrue(LiftedPalmBlob.pressLifts(heldThroughTicks(new HeldPress(Delivery.of(DeliveryKind.STREAM),
                    AbilityBadge.CHANNELED))), "a stream held should lift the blob");
        }

        @Test
        void aChanneledSelfPressLiftsTheBlob() {
            assertTrue(LiftedPalmBlob.pressLifts(heldThroughTicks(new HeldPress(Delivery.of(DeliveryKind.SELF),
                    AbilityBadge.CHANNELED))), "a channel run on the player should lift the blob");
        }

        @Test
        void aPreviewingPressLiftsTheBlob() {
            GloveInputGate press = heldThroughTicks(new HeldPress(Delivery.ARC, AbilityBadge.SELF));

            assertTrue(press.isPreviewing(), "a release ability held should preview");
            assertTrue(LiftedPalmBlob.pressLifts(press), "a release ability charging should lift the blob");
        }

        @Test
        void noPressLeavesTheBlobOnThePalm() {
            assertFalse(LiftedPalmBlob.pressLifts(new GloveInputGate()), "with no press the blob should rest");
        }

        @Test
        void aPressLetGoLeavesTheBlobOnThePalm() {
            HeldPress stream = new HeldPress(Delivery.of(DeliveryKind.STREAM), AbilityBadge.CHANNELED);
            GloveInputGate press = heldThroughTicks(stream);
            press.tick(false, stream);

            assertFalse(LiftedPalmBlob.pressLifts(press), "a press let go should let the blob settle");
        }

        @Test
        void anotherCastersSyncedHoldLiftsTheBlobAndItSettlesAfterTheHoldsGap() {
            long last = NOW + PalmBlobHop.LIFT_TICKS;
            PalmBlobLiftPayload held = new PalmBlobLiftPayload(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, false);
            try (SprayingClient client = SprayingClient.another(GooTypes.FROST)) {
                for (long tick = NOW; tick <= last; tick++) {
                    client.at(tick);
                    LiftedPalmBlob.liftArrived(held, tick);

                    assertTrue(LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).isPresent(),
                            "during the hold the caster's blob should stand off the palm, at tick " + tick);
                }
                client.at((long) Math.ceil(settledAt(last)));

                assertEquals(Optional.empty(), LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f),
                        "past the hold's gap and the settle the blob should rest on the palm");
            }
        }
    }

    @Nested
    class EasesOffThePalm {

        @Test
        void atTheSpraysFirstTickTheBlobStandsOnThePalmAtRestSize() {
            float share = LiftedPalmBlob.Lift.beginning(NOW, SprayingClient.SENT_GLOVE).shareAt(NOW);

            assertNear(PALM, LiftedPalmBlob.placeAt(share, PALM, SOURCE));
            assertEquals(KnivesCharge.PALM_REST, LiftedPalmBlob.sizeAt(share, NOW), DELTA);
        }

        @Test
        void partWayThroughTheEaseTheBlobStandsBetweenThePalmAndTheSourceAndBetweenItsSizes() {
            double partWay = NOW + PalmBlobHop.HOP_TICKS + PalmBlobHop.EASE_TICKS / 2;
            float share = sprayedThrough(NOW, EASED).shareAt(partWay);

            assertStrictlyBetween(LiftedPalmBlob.placeAt(share, PALM, SOURCE), PALM, SOURCE);
            float size = LiftedPalmBlob.sizeAt(share, partWay);
            assertTrue(size < KnivesCharge.PALM_REST && size > LiftedPalmBlob.liftedSizeAt(partWay),
                    "part way through the ease the size " + size + " should lie between rest and lifted");
        }

        @Test
        void atTheEasesEndTheBlobStandsAtTheSourceAtTheLiftedSize() {
            double easeEnd = NOW + PalmBlobHop.LIFT_TICKS;
            float share = sprayedThrough(NOW, EASED).shareAt(easeEnd);

            assertNear(SOURCE, LiftedPalmBlob.placeAt(share, PALM, SOURCE));
            assertEquals(LiftedPalmBlob.liftedSizeAt(easeEnd), LiftedPalmBlob.sizeAt(share, easeEnd), DELTA);
        }
    }

    @Nested
    class SettlesBackOntoThePalm {

        @Test
        void partWayThroughTheSettleTheBlobStandsBetweenTheSourceAndThePalm() {
            double partWay = partWayDown(EASED);
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
            long pressedAgain = (long) partWayDown(EASED);
            float reached = settling.shareAt(pressedAgain);

            LiftedPalmBlob.Lift turned = settling.sprayedAt(pressedAgain, SprayingClient.SENT_GLOVE);

            assertTrue(reached > 0f && reached < 1f, "the settle should be part way, at " + reached);
            assertEquals(reached, turned.shareAt(pressedAgain), DELTA,
                    "the blob should turn round from the share the settle reached");
            assertTrue(turned.sprayedAt(pressedAgain + 1, SprayingClient.SENT_GLOVE).shareAt(pressedAgain + 1)
                    > reached, "a tick later the blob should be lifting again");
        }
    }

    @Nested
    class PressesOnThePalmAndStandsIntoTheCube {

        @Test
        void onThePalmTheBlobIsWiderAndDeeperThanItIsTall() {
            BlobShape pressed = PalmBlobHop.hopPoseAt(0).shape();

            assertEquals(pressed.width(), pressed.depth(), DELTA, "the pressed blob should be as deep as it is wide");
            assertTrue(pressed.width() > pressed.height(), "the pressed blob " + pressed + " should be wider than tall");
        }

        @Test
        void atTheSprayingSourceTheBlobIsACube() {
            BlobShape lifted = PalmBlobHop.hopPoseAt(PalmBlobHop.LIFT_TICKS).shape();

            assertEquals(lifted.width(), lifted.height(), DELTA, "the lifted blob " + lifted + " should be a cube");
            assertEquals(lifted.depth(), lifted.height(), DELTA, "the lifted blob " + lifted + " should be a cube");
        }

        @Test
        void acrossTheWholeLiftTheBlobHoldsThePressedBlobsVolume() {
            float pressedVolume = volumeOf(PalmBlobHop.hopPoseAt(0).shape());
            for (int sample = 1; sample <= SAMPLES; sample++) {
                BlobShape shape = PalmBlobHop.hopPoseAt((double) PalmBlobHop.LIFT_TICKS * sample / SAMPLES).shape();
                assertEquals(pressedVolume, volumeOf(shape), VOLUME_TOLERANCE, "the blob " + shape
                        + " should hold the pressed blob's volume");
            }
        }
    }

    /**
     * The shape each pass draws a lifted blob in: the hand's pass for the
     * local first-person player and the level's pass for another player.
     */
    enum Pass {
        HAND {
            @Override
            BlobShape drawnAt(long now, long lastStreamTick) {
                try (SprayingClient client = SprayingClient.local(GooTypes.ZOO)) {
                    sprayThrough(client, now, lastStreamTick);
                    return LiftedPalmBlob.liftHeldInView().orElseThrow().shape();
                }
            }
        },
        LEVEL {
            @Override
            BlobShape drawnAt(long now, long lastStreamTick) {
                try (SprayingClient client = SprayingClient.another(GooTypes.ZOO)) {
                    sprayThrough(client, now, lastStreamTick);
                    return LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow().shape();
                }
            }
        };

        /**
         * The shape the pass draws the blob in at a time, its stream having
         * ticked every game tick from {@link #NOW} through its last tick.
         */
        abstract BlobShape drawnAt(long now, long lastStreamTick);

        private static void sprayThrough(SprayingClient client, long now, long lastStreamTick) {
            client.at(now);
            for (long tick = NOW; tick <= lastStreamTick; tick++) {
                LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, tick);
            }
        }
    }

    @Nested
    class CarriesOneShapeInBothPasses {

        @ParameterizedTest
        @EnumSource(Pass.class)
        void atTheSpraysFirstTickTheBlobIsThePressedPalmBlob(Pass pass) {
            assertShape(REST, pass.drawnAt(NOW, NOW));
        }

        @ParameterizedTest
        @EnumSource(Pass.class)
        void atTheLiftsEndTheBlobIsTheCube(Pass pass) {
            long easeEnd = NOW + PalmBlobHop.LIFT_TICKS;

            assertShape(BlobShape.CUBE.scaled(LiftedPalmBlob.liftedSizeAt(easeEnd)), pass.drawnAt(easeEnd, easeEnd));
        }

        @Test
        void atEveryTickOfTheLiftAndTheSettleBothPassesDrawOneShape() {
            long settled = (long) settledAt(EASED);
            for (long tick = NOW; tick < settled; tick++) {
                long lastStreamTick = Math.min(tick, EASED);

                assertShape(Pass.HAND.drawnAt(tick, lastStreamTick), Pass.LEVEL.drawnAt(tick, lastStreamTick));
            }
        }

        @Test
        void onceTheSettleEndsTheLiftsShapeIsThePressedPalmBlob() {
            double settled = settledAt(EASED);
            PalmBlobHop.Pose pose = sprayedThrough(NOW, EASED).poseAt(settled);

            assertShape(REST, LiftedPalmBlob.drawnShapeAt(pose, settled));
        }
    }

    /** Ticks between samples of the hop's pure function. */
    private static final double HOP_STEP = 0.05;

    /** The ticks of the hop's compression beat, sampled. */
    private static double[] compressionTicks() {
        return new double[] {PalmBlobHop.COMPRESS_TICKS / 2.0, PalmBlobHop.COMPRESS_TICKS};
    }

    @Nested
    class HopsOffThePalm {

        @Test
        void earlyInTheLiftTheBlobCompressesFlatterWithItsBottomOnThePalm() {
            for (double ticks : compressionTicks()) {
                PalmBlobHop.Pose pose = PalmBlobHop.hopPoseAt(ticks);

                assertTrue(pose.shape().height() < REST.height(), "compressed, the blob " + pose.shape()
                        + " should be shorter than the pressed palm blob " + REST);
                assertTrue(pose.shape().width() > REST.width() && pose.shape().depth() > REST.depth(),
                        "compressed, the blob " + pose.shape() + " should flare wider and deeper than " + REST);
                assertEquals(0f, pose.bottomOffThePalm(), DELTA, "compressed, the blob's bottom should rest on "
                        + "the palm");
            }
        }

        @ParameterizedTest
        @EnumSource(Pass.class)
        void bothPassesDrawTheCompression(Pass pass) {
            long ticks = PalmBlobHop.COMPRESS_TICKS;

            assertShape(PalmBlobHop.hopPoseAt(ticks).shape().scaled(KnivesCharge.PALM_REST),
                    pass.drawnAt(NOW + ticks, NOW + ticks));
        }

        @Test
        void theRecoilStretchesTheBlobUpTallerAndThinnerThanTheCubeBeforeItsBottomLeavesThePalm() {
            PalmBlobHop.Pose before = PalmBlobHop.hopPoseAt(0);
            double ticks = 0;
            while (PalmBlobHop.hopPoseAt(ticks).bottomOffThePalm() <= 0f) {
                before = PalmBlobHop.hopPoseAt(ticks);
                ticks += HOP_STEP;
                assertTrue(ticks < PalmBlobHop.LIFT_TICKS, "the blob's bottom should leave the palm during the lift");
            }

            assertTrue(before.shape().height() > 1f, "as its bottom leaves the palm the blob " + before.shape()
                    + " should be taller than the cube");
            assertTrue(before.shape().width() < 1f && before.shape().depth() < 1f, "as its bottom leaves the "
                    + "palm the blob " + before.shape() + " should be narrower than the cube");
            assertEquals(0f, before.bottomOffThePalm(), DELTA, "until then its bottom should rest on the palm");
        }

        @Test
        void acrossTheRecoilTheBlobNeverShortensNorWidens() {
            BlobShape before = PalmBlobHop.hopPoseAt(PalmBlobHop.COMPRESS_TICKS).shape();
            for (double ticks = PalmBlobHop.COMPRESS_TICKS; ticks <= PalmBlobHop.HOP_TICKS; ticks += HOP_STEP) {
                PalmBlobHop.Pose pose = PalmBlobHop.hopPoseAt(ticks);

                assertTrue(pose.shape().height() >= before.height(), "the recoil should never shorten the blob, "
                        + before + " to " + pose.shape());
                assertTrue(pose.shape().width() <= before.width(), "the recoil should never widen the blob, "
                        + before + " to " + pose.shape());
                assertEquals(0f, pose.bottomOffThePalm(), DELTA, "through the recoil the bottom should rest on "
                        + "the palm, at " + ticks);
                before = pose.shape();
            }
        }

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.client.ability.LiftedPalmBlobTest#goopAndSlick")
        void offThePalmTheBlobRisesToTheSourceAndSettlesIntoTheCube(ResourceKey<GooTypeDefinition> type) {
            try (SprayingClient client = SprayingClient.another(type)) {
                double farthest = 0;
                Vec3 palm = null;
                LiftedPalmBlob.Blob blob = null;
                for (long tick = NOW; tick <= EASED; tick++) {
                    client.at(tick);
                    LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, tick);
                    blob = LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow();
                    palm = palm == null ? blob.place() : palm;
                    if (tick >= NOW + PalmBlobHop.HOP_TICKS) {
                        double away = blob.place().distanceTo(palm);
                        assertTrue(away >= farthest - DELTA, "off the palm the blob should never draw back toward "
                                + "it, " + farthest + " to " + away + " at tick " + tick);
                        farthest = away;
                    }
                }

                assertEquals(blob.shape().width(), blob.shape().height(), DELTA, "at the lift's end " + blob.shape()
                        + " should be the cube");
                assertEquals(blob.shape().depth(), blob.shape().height(), DELTA, "at the lift's end " + blob.shape()
                        + " should be the cube");
                assertNear(SpraySource.inView(SprayingClient.EYE, SprayingClient.PITCH, SprayingClient.YAW, true),
                        blob.place());
            }
        }

        @Test
        void aSprayPressedAgainOffThePalmTurnsTheBlobRoundWhereItStandsWithNoHop() {
            long pressedAgain = (long) partWayDown(EASED);
            try (SprayingClient client = SprayingClient.another(GooTypes.ZOO)) {
                for (long tick = NOW; tick <= EASED; tick++) {
                    LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, tick);
                }
                client.at(pressedAgain);
                LiftedPalmBlob.Blob reached = LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow();

                LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, pressedAgain);
                LiftedPalmBlob.Blob turned = LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow();

                assertNear(reached.place(), turned.place());
                assertShape(reached.shape(), turned.shape());
            }
            LiftedPalmBlob.Lift turned = sprayedThrough(NOW, EASED).sprayedAt(pressedAgain, SprayingClient.SENT_GLOVE);
            assertTrue(turned.sprayedAt(pressedAgain + 1, SprayingClient.SENT_GLOVE).poseAt(pressedAgain + 1)
                    .bottomOffThePalm() > 0f, "a tick on, the turned blob's bottom should stand off the palm");
        }

        /** operator ruling 2026-11-13: the blob smears across the palm on contact as it lands */
        @Test
        void theLandingBlobMeetsThePalmAsTheCubeAndSmearsAcrossItIntoThePancake() {
            LiftedPalmBlob.Lift lift = sprayedThrough(NOW, EASED);
            double contact = endedAt(EASED) + PalmBlobHop.EASE_TICKS;
            PalmBlobHop.Pose landing = lift.poseAt(contact);

            assertEquals(0f, landing.bottomOffThePalm(), DELTA, "the blob's bottom should meet the palm");
            assertShape(BlobShape.CUBE, landing.shape());
            BlobShape before = landing.shape();
            for (double now = contact; now <= settledAt(EASED); now += HOP_STEP) {
                PalmBlobHop.Pose pose = lift.poseAt(now);

                assertEquals(0f, pose.bottomOffThePalm(), DELTA, "smearing, the bottom should stay on the palm");
                assertTrue(pose.shape().width() >= before.width() && pose.shape().height() <= before.height(),
                        "smearing, the blob should only spread and flatten, " + before + " to " + pose.shape());
                before = pose.shape();
            }
            assertShape(PalmBlobHop.pressed(), lift.poseAt(settledAt(EASED)).shape());
        }

        @Test
        void theBlobsBottomNeverPassesBelowThePalm() {
            LiftedPalmBlob.Lift lift = sprayedThrough(NOW, EASED);
            for (double now = NOW; now <= settledAt(EASED); now += HOP_STEP) {
                assertTrue(lift.poseAt(now).bottomOffThePalm() >= -DELTA, "the blob's bottom should never pass "
                        + "into the hand, at " + now);
            }
        }

        @Test
        void theSettleEndsWithThePressedPalmBlobOnThePalm() {
            PalmBlobHop.Pose settled = sprayedThrough(NOW, EASED).poseAt(settledAt(EASED));

            assertShape(PalmBlobHop.pressed(), settled.shape());
            assertEquals(0f, settled.bottomOffThePalm(), DELTA, "settled, the blob's bottom should rest on the palm");
        }
    }

    /** @return Goop's goo type and Slick's, the two sprays that lift the blob */
    static Stream<ResourceKey<GooTypeDefinition>> goopAndSlick() {
        return Stream.of(GooTypes.WEIRD, GooTypes.KINETIC);
    }

    private static float volumeOf(BlobShape shape) {
        return shape.width() * shape.depth() * shape.height();
    }

    private static void assertShape(BlobShape expected, BlobShape actual) {
        assertEquals(expected.width(), actual.width(), DELTA, "width of " + actual + ", expected " + expected);
        assertEquals(expected.depth(), actual.depth(), DELTA, "depth of " + actual + ", expected " + expected);
        assertEquals(expected.height(), actual.height(), DELTA, "height of " + actual + ", expected " + expected);
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
