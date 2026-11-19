package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.CalcifyStep;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.PetrifyStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.item.SprayerFlow;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.ConvergingCone;
import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A stream's program splits by pass: the steps needing the channel run on
 * the player over the cone's blocks and the rest on each entity in the cone
 * (decisions bore-vortex-with-a-worldspace-shake,
 * petrify-stone-encasement-and-calcify-map); a mob-first stream's swarm
 * goes for the mob lying nearest its aim, not the one nearest the player,
 * and seeks the living as wide and as far as its delivery names
 * (decision decay-gnats-degrade-each-block-once).
 */
class GooStreamHandlerTest {

    private static final Step PETRIFY = new PetrifyStep(Expr.literal(2));
    private static final Step CALCIFY = new CalcifyStep(Identifier.fromNamespaceAndPath("goo", "calcify"), 30);

    @Test
    void channelStepsRunInTheBlockPassAndTheRestInTheEntityPass() {
        List<Step> program = List.of(PETRIFY, CALCIFY);
        assertEquals(List.of(CALCIFY), GooStreamHandler.channelSteps(program, true));
        assertEquals(List.of(PETRIFY), GooStreamHandler.channelSteps(program, false));
    }

    @Nested
    class MoteLaunch {

        private static final Vec3 HEADING = new Vec3(0.6, 0.0, 0.8);
        private static final double SPEED = 0.5;
        private static final double DELTA = 1e-12;

        @Test
        void aMoteLaunchesAlongItsHeadingAtItsSpeedAlone() {
            Vec3 launch = GooStreamHandler.moteLaunch(HEADING, SPEED);

            assertEquals(0.6 * SPEED, launch.x, DELTA);
            assertEquals(0, launch.y, DELTA);
            assertEquals(0.8 * SPEED, launch.z, DELTA);
        }
    }

    /**
     * A stream's mote off the arc launches along its heading at the speed
     * that rests it as far off as the crosshair lands, or at the stream's
     * range past it.
     * mycosis-spore-stream-buds-and-poisons
     */
    @Nested
    class SprayLaunch {

        private static final Identifier SPORE = Identifier.fromNamespaceAndPath("goo", "spore");
        private static final Vec3 GLOVE = new Vec3(0.3, 1.3, 0.2);
        private static final double RANGE = 8;
        private static final CoatRay.ConeShare AXIS = new CoatRay.ConeShare(0, 0);
        private static final double TOLERANCE = 1e-9;

        private void assertNear(Vec3 expected, Vec3 actual) {
            assertEquals(expected.x, actual.x, TOLERANCE, "x");
            assertEquals(expected.y, actual.y, TOLERANCE, "y");
            assertEquals(expected.z, actual.z, TOLERANCE, "z");
        }

        @Test
        void aMoteLaunchesAtTheCrosshairShortOfTheRange() {
            assertCrosshairLaunch(3.5, 3.5);
        }

        @Test
        void aMoteLaunchesAtTheRangeWhenTheCrosshairLiesPastIt() {
            assertCrosshairLaunch(12, RANGE);
        }

        private void assertCrosshairLaunch(double crosshairDistance, double restsAt) {
            Vec3 axis = new Vec3(0.6, 0, 0.8);
            StreamMotesPayload spores = new StreamMotesPayload(1, GLOVE, axis, SPORE, 0,
                    GooStreamHandler.crosshairSpeed(RANGE, crosshairDistance), Delivery.NO_THROW_SPEED, false, 0, 0,
                    GLOVE, 0);

            Vec3 launch = GooStreamHandler.moteLaunch(spores, AXIS);

            assertNear(axis.scale(restsAt * 0.1), launch);
        }
    }
    /**
     * A Goop spray's drips leave the lifted blob, out to the glove side of
     * the eye, and converge on the cone centered on the look: pointed straight
     * down or at a face a block off, every drip strikes the block looked at; a
     * few blocks back they scatter around where the look lands; sprayed at the
     * sky they come down on the caster's own line. Each drip is flown through
     * DripArc against faked blocks, as DripFlights flies the server's drips.
     * residual-spray-cone-centers-on-the-look-from-the-blob
     * spray-drips-land-around-what-the-look-points-at
     * diagnose-and-correct-the-spray-turning-right-at-point-blank
     */
    @Nested
    class DripsConvergeOnTheLook {

        /** The hold ticks a point blank spray is read over. */
        private static final int POINT_BLANK_TICKS = 20;
        /** The hold ticks a spray's scatter is read over. */
        private static final int SCATTER_TICKS = 100;
        /** How near the mean strike lies to where the look lands, a few blocks back. */
        private static final double ON_THE_LOOK = 0.1;
        /** How near the caster's line the mean strike of a spray at the sky lies, sideways. */
        private static final double ON_THE_LINE = 0.25;
        /** The lowest a drip may fly before it reads as gone: below the world. */
        private static final double BELOW_THE_WORLD = 0;
        /**
         * Where the lifted blob stands in the caster's view, worked out apart from the code under test: out to
         * the right glove's side, a little below the look's line up the view, and ahead along the look, as
         * SpraySource puts it over the resting palm.
         */
        private static final double BLOB_RIGHT = 0.56;
        private static final double BLOB_UP = -0.15;
        private static final double BLOB_AHEAD = 0.716;
        private final Delivery goop = AbilityJson.decode("weird_goop").delivery();
        private final double exitSpeed = goop.exitSpeedAt(0);

        @Test
        void everyDripPointedStraightDownStrikesTheBlockLookedAt() {
            // standing on the block at (0, 63, 0), looking straight down at its top face's center, facing south
            Vec3 eye = new Vec3(0.5, 64 + 1.62, 0.5);
            Vec3 look = new Vec3(0, -1, 0);
            Vec3 blob = blobOf(eye, look, new Vec3(0, 0, 1));
            BlockPos lookedAt = new BlockPos(0, 63, 0);

            for (CoatRay.BlockStrike strike : strikesOver(POINT_BLANK_TICKS, eye, look, blob, floorAt(64))) {
                assertEquals(lookedAt, strike.block(), "pointed straight down at " + lookedAt + " from " + eye
                        + ", a drip from the blob at " + blob + " should strike it, strikes " + strike.point()
                        + ", " + offTheLook(eye, look, strike.point()) + " off the look");
            }
        }

        @Test
        void everyDripStrikesAFaceOneBlockAway() {
            // looking east at the center of the west face of the block at (1, 65, 0), one block off
            Vec3 eye = new Vec3(0, 65.5, 0.5);
            Vec3 look = new Vec3(1, 0, 0);
            Vec3 blob = blobOf(eye, look, new Vec3(0, 1, 0));
            BlockPos lookedAt = new BlockPos(1, 65, 0);

            for (CoatRay.BlockStrike strike : strikesOver(POINT_BLANK_TICKS, eye, look, blob, wallAt(1))) {
                assertEquals(lookedAt, strike.block(), "looking at " + lookedAt + " a block off, a drip from the"
                        + " blob at " + blob + " should strike its face, strikes " + strike.point());
            }
        }

        @Test
        void dripsScatterAroundTheBlockLookedAtFromAFewBlocksBack() {
            Vec3 eye = new Vec3(0, 65.5, 0.5);
            Vec3 look = new Vec3(1, 0, 0);
            Vec3 landsAt = new Vec3(4, 65.5, 0.5);
            List<CoatRay.BlockStrike> strikes = strikesOver(SCATTER_TICKS, eye, look, blobOf(eye, look,
                    new Vec3(0, 1, 0)), wallAt(4));
            double rim = landsAt.distanceTo(eye) * Math.tan(Math.toRadians(goop.coneDegrees() / 2));

            Vec3 mean = meanOf(strikes);
            assertTrue(mean.distanceTo(landsAt) <= ON_THE_LOOK, "the strikes should average on where the look"
                    + " lands, " + landsAt + ", average on " + mean);
            assertTrue(strikes.stream().anyMatch(strike -> strike.point().distanceTo(landsAt) > rim / 2),
                    "some strike should lie past half the cone's radius there, " + rim / 2);
        }

        @Test
        void sprayAtTheSkyLandsCenteredOnThePlayersLine() {
            // facing south, pitched 45 degrees up, the floor at the caster's feet
            Vec3 eye = new Vec3(0.5, 64 + 1.62, 0.5);
            Vec3 look = new Vec3(0, 1, 1).normalize();
            Vec3 up = new Vec3(0, 1, -1).normalize();
            List<CoatRay.BlockStrike> strikes = strikesOver(SCATTER_TICKS, eye, look, blobOf(eye, look, up),
                    floorAt(64));
            List<Double> sideways = strikes.stream().map(strike -> strike.point().x - eye.x).toList();

            double mean = sideways.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            assertTrue(Math.abs(mean) <= ON_THE_LINE, "the strikes should average on the caster's line, average "
                    + mean + " off it");
            assertTrue(sideways.stream().anyMatch(off -> off > 0) && sideways.stream().anyMatch(off -> off < 0),
                    "strikes should fall on both sides of the caster's line");
        }

        @Test
        void everyServerDripLeavesTheBlob() {
            Vec3 eye = new Vec3(0, 65.5, 0.5);
            Vec3 look = new Vec3(1, 0, 0);
            Vec3 blob = blobOf(eye, look, new Vec3(0, 1, 0));

            for (DripFlights.Flight flight : flightsOf(1, eye, look, blob, wallAt(4))) {
                assertEquals(blob, flight.position(), "a server drip should leave the blob");
            }
        }

        /** The blob in a view: out to the right glove's side, below the look up the view, ahead along the look. */
        private static Vec3 blobOf(Vec3 eye, Vec3 look, Vec3 upTheView) {
            Vec3 right = look.cross(upTheView).normalize();
            return eye.add(right.scale(BLOB_RIGHT)).add(upTheView.scale(BLOB_UP)).add(look.scale(BLOB_AHEAD));
        }

        /** How far off the look a point lies, in degrees from the eye. */
        private static double offTheLook(Vec3 eye, Vec3 look, Vec3 point) {
            return Math.toDegrees(Math.acos(point.subtract(eye).normalize().dot(look.normalize())));
        }

        private List<DripFlights.Flight> flightsOf(int held, Vec3 eye, Vec3 look, Vec3 blob, CoatRay.BlockClip clip) {
            ConvergingCone cone = ConvergingCone.along(eye, look, exitSpeed, flightThrough(clip));
            return GooStreamHandler.flightsOf(new DripFlights.Thrower(null, null, 0, null, Optional.empty(), false),
                    new GooStreamHandler.DripThrow(blob, cone, exitSpeed, DripArc.GRAVITY), goop,
                    CoatRay.scatteredShares(held, goop.dripsPerTick()));
        }

        /** Every drip of a run of hold ticks, flown to where it strikes; a drip ending unstruck fails the test. */
        private List<CoatRay.BlockStrike> strikesOver(int ticks, Vec3 eye, Vec3 look, Vec3 blob,
                                                      CoatRay.BlockClip clip) {
            List<CoatRay.BlockStrike> strikes = new ArrayList<>();
            for (int held = 1; held <= ticks; held++) {
                for (DripFlights.Flight flight : flightsOf(held, eye, look, blob, clip)) {
                    DripArc.Step end = DripArc.flyToItsEnd(flight.position(), flight.velocity(), flightThrough(clip));
                    strikes.add(end.strike().orElseThrow(() -> new AssertionError("a drip from " + blob
                            + " should strike a block, flew off unstruck at " + end.position())));
                }
            }
            return strikes;
        }

        private static DripArc.Flight flightThrough(CoatRay.BlockClip clip) {
            return new DripArc.Flight(clip, DripArc.NO_LIVING, point -> point.y > BELOW_THE_WORLD, DripArc.GRAVITY);
        }

        private static Vec3 meanOf(List<CoatRay.BlockStrike> strikes) {
            Vec3 sum = strikes.stream().map(CoatRay.BlockStrike::point).reduce(Vec3.ZERO, Vec3::add);
            return sum.scale(1.0 / strikes.size());
        }

        /** A floor whose top face stands at a height, struck from above. */
        private static CoatRay.BlockClip floorAt(double top) {
            return (from, to) -> {
                if (from.y < top || to.y >= top) {
                    return Optional.empty();
                }
                Vec3 point = from.lerp(to, (from.y - top) / (from.y - to.y));
                return Optional.of(new CoatRay.BlockStrike(point, BlockPos.containing(point.x, top - 0.5, point.z),
                        Direction.UP));
            };
        }

        /** A wall whose west face stands at an x, struck from the west. */
        private static CoatRay.BlockClip wallAt(double face) {
            return (from, to) -> {
                if (from.x > face || to.x <= face) {
                    return Optional.empty();
                }
                Vec3 point = from.lerp(to, (face - from.x) / (to.x - from.x));
                return Optional.of(new CoatRay.BlockStrike(point, BlockPos.containing(face + 0.5, point.y, point.z),
                        Direction.WEST));
            };
        }
    }

    @Nested
    class SwarmPick {

        private static final Vec3 EYE = Vec3.ZERO;
        private static final Vec3 LOOK = new Vec3(1, 0, 0);
        private static final double HALF_A_MOB = 0.3;

        private Mob mobAt(Vec3 middle) {
            Mob mob = mock(Mob.class);
            when(mob.getBoundingBox()).thenReturn(new AABB(middle, middle).inflate(HALF_A_MOB));
            return mob;
        }

        @Test
        void theSwarmGoesForTheMobNearestItsAimOverTheMobNearestThePlayer() {
            // two blocks out and 40 degrees off the aim
            Mob near = mobAt(new Vec3(2 * Math.cos(Math.toRadians(40)), 0, 2 * Math.sin(Math.toRadians(40))));
            // six blocks out and 5 degrees off the aim
            Mob aligned = mobAt(new Vec3(6 * Math.cos(Math.toRadians(5)), 0, 6 * Math.sin(Math.toRadians(5))));
            List<LivingEntity> inTheCone = List.of(near, aligned);

            assertEquals(List.of(aligned), GooStreamHandler.mostAlignedMob(inTheCone, EYE, LOOK));
        }
    }

    /**
     * Decay's swarm seeks a mob within its delivery's 45 degrees either side of the aim and 8 blocks out,
     * past the 6 it paints blocks within (decision decay-gnats-degrade-each-block-once).
     */
    @Nested
    class DecaysSeek {

        private static final Vec3 EYE = Vec3.ZERO;
        private static final Vec3 LOOK = new Vec3(1, 0, 0);
        private static final double HALF_A_MOB = 0.3;
        private final Delivery decay = AbilityJson.decode("nether_decay").delivery();

        private Mob mobAt(Vec3 middle) {
            Mob mob = mock(Mob.class);
            when(mob.getBoundingBox()).thenReturn(new AABB(middle, middle).inflate(HALF_A_MOB));
            return mob;
        }

        @Test
        void theSwarmReachesAMobAboutThirtyFourDegreesOffTheAim() {
            assertTrue(GooStreamHandler.inCone(EYE, LOOK, decay, mobAt(new Vec3(3, 0, 2))));
        }

        @Test
        void theSwarmReachesAMobSevenBlocksOutPastWhereItPaints() {
            assertTrue(GooStreamHandler.inCone(EYE, LOOK, decay, mobAt(new Vec3(7, 0, 0))));
        }
    }

    /**
     * The sprayer's flow scales how many drips a tick it throws, over a hold
     * (decision spray-item-sprays-more-or-less-per-tick).
     */
    @Nested
    class FlowScalesTheDrips {

        private static final int HOLD_TICKS = 20;
        private final Delivery goop = AbilityJson.decode("weird_goop").delivery();

        @Test
        void higherFlowThrowsMoreDripsATick() {
            int lowest = 0;
            int highest = 0;
            for (int held = 1; held <= HOLD_TICKS; held++) {
                lowest += GooStreamHandler.dripsThisTick(null, goop, Optional.empty(), held,
                        SprayerFlow.lowest().rate());
                highest += GooStreamHandler.dripsThisTick(null, goop, Optional.empty(), held,
                        SprayerFlow.highest().rate());
            }
            assertTrue(highest > lowest, "highest flow " + highest + " drips, lowest " + lowest);
        }
    }
}
