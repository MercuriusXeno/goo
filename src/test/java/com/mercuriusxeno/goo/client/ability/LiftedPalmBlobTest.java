package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.crystal.StackHover;
import com.mercuriusxeno.goo.client.model.FirstPersonSwing;
import com.mercuriusxeno.goo.client.throwing.BlobShape;
import com.mercuriusxeno.goo.client.throwing.GloveInputGate;
import com.mercuriusxeno.goo.network.PalmBlobLiftPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    private static final BlobShape REST = PalmBlobHop.pressed();
    /** The palm blob's rest size, the size every other size is against. */
    private static final float PALM_REST = 1f;

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

                assertNear(SpraySource.of(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, 0f), blob.place());
                assertEquals(GooTypes.ZOO, blob.type());
            }
        }

        // decision third-person-blob-stands-above-the-drawn-palm
        @Test
        void anotherPlayersBlobStandsAboveTheirDrawnPalmInTheGlovesType() {
            try (SprayingClient client = SprayingClient.another(GooTypes.FROST)) {
                client.drawsThePalm(BlobRestTest.DRAWN);
                client.at(EASED);
                sprayEveryTickThroughTheEase();

                LiftedPalmBlob.Blob blob = LiftedPalmBlob.blobOf(SprayingClient.CASTER, 0f).orElseThrow();

                assertNear(BlobRestTest.DRAWN.above(SpraySource.RISE_ABOVE_THE_PALM), blob.place());
                assertEquals(GooTypes.FROST, blob.type());
            }
        }

        // decision third-person-blob-stands-above-the-drawn-palm: the lift starts where the model draws the palm
        @Test
        void anotherPlayersBlobLiftsFromTheirDrawnPalm() {
            try (SprayingClient client = SprayingClient.another(GooTypes.FROST)) {
                client.drawsThePalm(BlobRestTest.DRAWN);
                client.at(NOW);
                LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, NOW);

                assertNear(BlobRestTest.DRAWN.place(), LiftedPalmBlob.placeOf(SprayingClient.CASTER, 0f)
                        .orElseThrow());
            }
        }
    }

    /**
     * Outside the centered abilities and Bore, the local first-person blob
     * lifts to a still place straight above the resting palm, the palm with
     * no bob and no swing, at any field of view, and a Goop or Slick drip
     * first shows there.
     * operator ruling: every ability outside the eight centered ones and Bore lifts its blob straight above
     * where the hand rests, never bobbing or swinging
     * palm-blob-lifts-as-the-spray-source
     */
    @Nested
    class RisesStillAboveTheHand {

        /** Where the resting palm stands in the view: down, out to the right and ahead. */
        private static final Vector3fc PALM_IN_VIEW = new Vector3f(0.5f, -0.4f, -0.75f);
        /** The palm at the glove model's origin. */
        private static final Vector3fc PALM_IN_MODEL = new Vector3f();
        /** How far across the view's up the blob may stand off the resting palm. */
        private static final double ACROSS = 1e-3;
        /** How far two places a float pose stack carries may stray and still be one place. */
        private static final double SAME_PLACE = 1e-5;
        private static final Matrix4fc NO_BOB = new Matrix4f();
        /** The hand's pose down to the palm before any swing, turned some way. */
        private static final Matrix4fc BEFORE_THE_SWING = new Matrix4f().translation(PALM_IN_VIEW)
                .rotateXYZ(0.3f, -0.6f, 0.1f);
        /** A swing part way through: the hand carried across and down and tipped, as ItemInHandRenderer.swingArm. */
        private static final Matrix4fc THE_SWING = new Matrix4f().translation(-0.15f, 0.12f, -0.1f)
                .rotateXYZ(-0.9f, 0.4f, -0.3f);

        /** A walking view's bob as GameRenderer.bobView turns it: down and aside, rolled and pitched. */
        private static Matrix4f walkingBob() {
            return new Matrix4f().translate(0.04f, -0.08f, 0f).rotateZ((float) Math.toRadians(2.4))
                    .rotateX((float) Math.toRadians(3.5));
        }

        @org.junit.jupiter.api.AfterEach
        void forgetTheSwing() {
            FirstPersonSwing.forgetSwing();
        }

        /** The fully lifted blob's place in the hand pass, its glove drawn under a bob down to the palm. */
        private static Vec3 liftedUnder(SprayingClient client, Matrix4fc bob, Matrix4fc handToPalm) {
            client.bobbing(bob);
            SpraySource.recordRestingPalm(client.gloveDrawnUnder(bob, handToPalm), PALM_IN_MODEL);
            client.at(EASED);
            sprayEveryTickThroughTheEase();
            LiftedPalmBlob.HandLift lift = LiftedPalmBlob.liftHeldInView().orElseThrow();
            assertEquals(1f, lift.share(), DELTA, "the blob should be fully lifted");
            return lift.sourceInHand();
        }

        private static void assertStraightAbove(Vec3 palm, Vec3 place, Vec3 up) {
            Vec3 off = place.subtract(palm);
            assertTrue(off.dot(up) > 0, "the blob " + place + " should stand above the resting palm " + palm);
            assertEquals(0, off.subtract(up.scale(off.dot(up))).length(), ACROSS, "the blob " + place
                    + " should stand straight above the resting palm " + palm + ", off it by " + off);
        }

        @ParameterizedTest
        @ValueSource(floats = {70f, 110f})
        void theFullyLiftedBlobStandsStraightAboveTheRestingPalm(float fov) {
            try (SprayingClient client = SprayingClient.local(GooTypes.ZOO)) {
                client.viewedAt(fov);

                Vec3 place = liftedUnder(client, NO_BOB, BEFORE_THE_SWING);

                assertStraightAbove(client.inTheHandPass(PALM_IN_VIEW), place, client.viewUp());
            }
        }

        @Test
        void theFullyLiftedBlobHoldsOnePlaceInTheViewUnderEveryBob() {
            try (SprayingClient client = SprayingClient.local(GooTypes.ZOO)) {
                Vec3 still = liftedUnder(client, NO_BOB, BEFORE_THE_SWING);
                Vec3 walking = liftedUnder(client, walkingBob(), BEFORE_THE_SWING);

                assertEquals(0, still.distanceTo(walking), SAME_PLACE, "the blob should stand at " + still
                        + " under a walking bob, stands at " + walking);
            }
        }

        @Test
        void theFullyLiftedBlobHoldsOnePlaceInTheViewThroughASwing() {
            try (SprayingClient client = SprayingClient.local(GooTypes.ZOO)) {
                Vec3 unswung = liftedUnder(client, NO_BOB, BEFORE_THE_SWING);
                FirstPersonSwing.recordSwing(client.gloveDrawnUnder(NO_BOB, BEFORE_THE_SWING),
                        client.gloveDrawnUnder(NO_BOB, new Matrix4f(BEFORE_THE_SWING).mul(THE_SWING)));
                Vec3 swung = liftedUnder(client, NO_BOB, new Matrix4f(BEFORE_THE_SWING).mul(THE_SWING));

                assertEquals(0, unswung.distanceTo(swung), SAME_PLACE, "the blob should stand at " + unswung
                        + " through a swing, stands at " + swung);
            }
        }

        @Test
        void aGoopDripFirstShowsAtTheLiftedBlobStraightAboveTheRestingPalm() {
            try (SprayingClient client = SprayingClient.local(GooTypes.WEIRD)) {
                liftedUnder(client, NO_BOB, BEFORE_THE_SWING);

                Vec3 place = LiftedPalmBlob.placeOf(SprayingClient.CASTER, 0f).orElseThrow();
                Vec3 drip = LiftedPalmBlob.dripLeavesFrom(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, 0f);

                assertNear(place, drip);
                Vec3 palm = SprayingClient.EYE.add(HandPassView.fromHandPass(client.inTheHandPass(PALM_IN_VIEW),
                        client.camera().rotation(), SprayingClient.WORLD_FOV, NO_BOB));
                assertStraightAbove(palm, place, client.viewUp());
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
            assertTrue(greatest < PALM_REST, "the greatest lifted size " + greatest
                    + " should be below the palm blob's rest size " + PALM_REST);
            assertTrue(greatest > least, "the lifted size should beat, from " + least + " to " + greatest);
        }

        /** The lifted blob beats every 12 ticks for every cast (decision every-lifted-palm-blob-beats-once-a-second). */
        @Test
        void theLiftedSizeRepeatsEveryTwelveTicksNotEveryTwenty() {
            boolean differsTwentyLater = false;
            for (int sample = 0; sample < SAMPLES; sample++) {
                double time = NOW + TWELVE_TICKS * sample / (double) SAMPLES;
                float size = LiftedPalmBlob.liftedSizeAt(time);
                assertEquals(size, LiftedPalmBlob.liftedSizeAt(time + TWELVE_TICKS), 1e-4,
                        "the size twelve ticks later should be the size now");
                differsTwentyLater |= Math.abs(size - LiftedPalmBlob.liftedSizeAt(time + ONE_SECOND)) > 1e-4;
            }
            assertTrue(differsTwentyLater, "the size should not repeat every twenty ticks");
        }

        /**
         * While Blast charges, the palm blob and the rooted spheroid swell most
         * on each stage step of the hold and least midway, whatever game tick
         * the hold began on (decision every-lifted-palm-blob-beats-once-a-second).
         */
        @ParameterizedTest
        @ValueSource(longs = {NOW, NOW + 7})
        void aChargesBeatSwellsMostOnEachStepAndLeastMidway(long began) {
            for (int step = ONE_SECOND; step <= FULL_CHARGE; step += ONE_SECOND) {
                double phase = LiftedPalmBlob.chargeBeatPhase(began, began + step, ONE_SECOND);
                assertEquals(LiftedPalmBlob.LIFTED_GREATEST, LiftedPalmBlob.liftedSizeAtPhase(phase), 1e-4,
                        "the blob at held tick " + step);
                assertEquals(1 + ChargeMotes.SWELL_DEPTH, ChargeMotes.swellAtPhase(phase), 1e-6,
                        "the spheroid at held tick " + step);
            }
            for (int midway = ONE_SECOND + ONE_SECOND / 2; midway < FULL_CHARGE; midway += ONE_SECOND) {
                double phase = LiftedPalmBlob.chargeBeatPhase(began, began + midway, ONE_SECOND);
                assertEquals(LiftedPalmBlob.LIFTED_LEAST, LiftedPalmBlob.liftedSizeAtPhase(phase), 1e-4,
                        "the blob at held tick " + midway);
                assertEquals(1, ChargeMotes.swellAtPhase(phase), 1e-6, "the spheroid at held tick " + midway);
            }
        }
    }

    private static final int ONE_SECOND = 20;
    private static final int TWELVE_TICKS = 12;
    private static final int FULL_CHARGE = 100;

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

        // residual-badge-marks-the-four-goo-sprays
        @ParameterizedTest
        @EnumSource(value = AbilityBadge.class, names = {"CHANNELED", "RESIDUAL"})
        void aStreamingPressLiftsTheBlob(AbilityBadge badge) {
            assertTrue(LiftedPalmBlob.pressLifts(heldThroughTicks(new HeldPress(Delivery.of(DeliveryKind.STREAM),
                    badge))), "a stream held should lift the blob");
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
            PalmBlobLiftPayload held = new PalmBlobLiftPayload(SprayingClient.CASTER, SprayingClient.SENT_GLOVE);
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
            assertEquals(PALM_REST, LiftedPalmBlob.sizeAt(share, NOW), DELTA);
        }

        @Test
        void partWayThroughTheEaseTheBlobStandsBetweenThePalmAndTheSourceAndBetweenItsSizes() {
            double partWay = NOW + PalmBlobHop.HOP_TICKS + PalmBlobHop.EASE_TICKS / 2;
            float share = sprayedThrough(NOW, EASED).shareAt(partWay);

            assertStrictlyBetween(LiftedPalmBlob.placeAt(share, PALM, SOURCE), PALM, SOURCE);
            float size = LiftedPalmBlob.sizeAt(share, partWay);
            assertTrue(size < PALM_REST && size > LiftedPalmBlob.liftedSizeAt(partWay),
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
            assertEquals(PALM_REST, LiftedPalmBlob.sizeAt(share, settled), DELTA);
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

            assertShape(PalmBlobHop.hopPoseAt(ticks).shape().scaled(PALM_REST),
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
                client.drawsThePalm(BlobRestTest.DRAWN);
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
                // decision third-person-blob-stands-above-the-drawn-palm: the source stands above the drawn palm
                assertNear(BlobRestTest.DRAWN.above(SpraySource.RISE_ABOVE_THE_PALM), blob.place());
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

    /**
     * The level's pass faces every caster's lifted blob along the caster's
     * look, whatever the hand's turn.
     */
    @Test
    void theLevelsPassFacesTheBlobAlongTheCastersLook() {
        try (SprayingClient client = SprayingClient.another(GooTypes.FROST)) {
            client.at(NOW);
            Player caster = (Player) Minecraft.getInstance().level.getEntity(SprayingClient.CASTER);

            assertEquals(LiftedPalmBlob.facingTheLook(SprayingClient.PITCH, SprayingClient.YAW),
                    LiftedPalmBlob.facingOf(caster, 0f));
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

    /**
     * The cube a held stack shrinks is the cube the palm blob draws: the cube
     * the stack's slots stand over has the drawn lifted cube's greatest edge,
     * and the drawn cube eases a step smaller as each knife or javelin forms,
     * over the ticks the one forming takes to rise, rather than snapping to it
     * (decisions knives-blob-shrinks-per-knife-and-vanishes-on-the-last,
     * dart-javelins-hover-over-the-shrinking-blob-cube).
     */
    @Nested
    class StackCube {

        private static final int CASTER = 31;

        @org.junit.jupiter.api.AfterEach
        void clearTheStacks() {
            HoveringKnives.clear();
            HeldJavelins.clear();
        }

        @Test
        void theCubeTheStackHoversOverIsTheDrawnLiftedCube() {
            double drawn = 2 * com.mercuriusxeno.goo.client.throwing.GooFlightRenderer.SHELL_HW
                    * LiftedPalmBlob.FLIGHT_SIZE_OF_PALM_REST * LiftedPalmBlob.LIFTED_GREATEST;
            assertEquals(drawn, StackHover.cubeEdgeAt(0, StackHover.KNIVES), DELTA);
            assertEquals(drawn, StackHover.cubeEdgeAt(0, StackHover.JAVELINS), DELTA);
        }

        @Test
        void theCubeEasesASizeSmallerAsAKnifeFormsRatherThanSnapping() {
            HoveringKnives.formed(CASTER, 3, NOW);
            HoveringKnives.formed(CASTER, 4, NOW + 10);
            float before = StackHover.cubeShareAt(3, StackHover.KNIVES);
            float after = StackHover.cubeShareAt(4, StackHover.KNIVES);

            assertEquals(before, LiftedPalmBlob.stackShareOf(CASTER, NOW + 10), DELTA);
            float midway = LiftedPalmBlob.stackShareOf(CASTER, NOW + 10 + HoveringKnives.EASE_TICKS / 2);
            assertTrue(midway < before && midway > after, "midway the cube should stand between, at " + midway);
            assertEquals(after, LiftedPalmBlob.stackShareOf(CASTER, NOW + 10 + HoveringKnives.EASE_TICKS), DELTA);
        }

        @Test
        void theCubeEasesASizeSmallerAsAJavelinFormsRatherThanSnapping() {
            HeldJavelins.formed(CASTER, 1, NOW);
            float before = StackHover.cubeShareAt(0, StackHover.JAVELINS);

            assertEquals(before, LiftedPalmBlob.stackShareOf(CASTER, NOW), DELTA);
            assertEquals(StackHover.cubeShareAt(1, StackHover.JAVELINS),
                    LiftedPalmBlob.stackShareOf(CASTER, NOW + HoveringKnives.EASE_TICKS), DELTA);
        }
    }
}