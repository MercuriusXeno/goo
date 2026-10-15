package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SiphonRule;
import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stream flows languidly along its path, kept the longest route's travel
 * at the base pace past its drain; between the ends it snakes off the
 * straight line and wanders, its skin a cyclone whose grooves twist along the
 * liquid and spin about its axis as the goo travels, tapering to a point at
 * both ends (decisions unmake-waves-dissolve-by-crucible-cost and
 * unmake-stream-is-a-shrinking-waterspout).
 */
class DrinkStreamTest {

    private static final double DELTA = 1e-9;
    private static final long START = 100;
    private static final long END = 134;
    private static final DrinkPayload.Streaming BLOCK = new DrinkPayload.Streaming(BlockPos.ZERO,
            START - SiphonRule.INJECT_TICKS, START, END);
    private static final Vec3 FROM = new Vec3(7, 2.5, 3.5);
    private static final Vec3 TO = new Vec3(1.5, 2.2, 3.1);
    private static final long SEED = 42;
    private static final DrinkStream.Path PATH = new DrinkStream.Path(FROM, TO, SEED);
    private static final double LATER = 100;
    private static final double LENGTH = 3;
    private static final double MIDDLE = 1.5;
    private static final Vec3 GLOVE = new Vec3(1.5, 2.2, 3.1);
    /** A block streaming, landed at tick 100 and drained long after the test looks. */
    private static final DrinkTree.Block STREAMING = new DrinkTree.Block(new BlockPos(7, 2, 3),
            new Vec3(7.5, 2.5, 3.5), 1, START - SiphonRule.INJECT_TICKS, START, 1000);
    /** A time the stream's head is well past the ring the spin test reads. */
    private static final double FLOWING = START + 20;
    private static final int TICKS_A_SECOND = 20;
    private static final double EIGHT_BLOCKS = 8;
    private static final double OFF_THE_LINE = 0.02;
    private static final double RADIUS = 0.1;
    private static final double NUDGE = 0.01;
    private static final Vec3 PULL = new Vec3(-1, 0, 0);
    /** Radians a tick under which the grooves would read as standing still. */
    private static final double TURNING = 0.05;

    @Nested
    class Timing {

        @Test
        void aStreamIsKeptTheLongestRoutesTravelPastItsDrain() {
            assertFalse(DrinkStream.gone(BLOCK, END + DrinkStream.LONGEST_TRAVEL_TICKS - 1));
            assertTrue(DrinkStream.gone(BLOCK, END + DrinkStream.LONGEST_TRAVEL_TICKS));
        }

        @Test
        void theFlowIsEightBlocksASecondTheServersPaceAndTheLongestRouteOutlastsTheCone() {
            double longestRoute = SiphonRule.RANGE + DrinkStream.GLOVE_SLACK;
            assertEquals(EIGHT_BLOCKS, DrinkStream.FLOW * TICKS_A_SECOND, DELTA);
            assertEquals(SiphonRule.BASE_PACE, DrinkStream.FLOW, DELTA);
            assertTrue(DrinkStream.LONGEST_TRAVEL_TICKS * DrinkStream.FLOW >= longestRoute);
        }
    }

    @Nested
    class Path {

        @Test
        void theStreamLeavesThePathsStartAndReachesItsEnd() {
            assertEquals(0, DrinkStream.pointAt(PATH, 0, START).distanceTo(FROM), DELTA);
            assertEquals(0, DrinkStream.pointAt(PATH, 1, START).distanceTo(TO), DELTA);
        }

        @Test
        void theStreamSnakesOffTheLineAndWanders() {
            Vec3 straight = FROM.lerp(TO, 0.5);
            Vec3 bent = DrinkStream.pointAt(PATH, 0.5, START);
            Vec3 later = DrinkStream.pointAt(PATH, 0.5, START + LATER);

            assertTrue(bent.distanceTo(straight) > OFF_THE_LINE, "the middle leaves the straight line");
            assertTrue(bent.distanceTo(later) > OFF_THE_LINE, "the bend moves with time");
            assertTrue(bent.distanceTo(straight) <= 2 * DrinkStream.SNAKE + DELTA);
        }

        @Test
        void theNearestShareIsWhereThePointProjectsOntoTheLine() {
            Vec3 beside = TO.subtract(FROM).cross(new Vec3(0, 1, 0)).normalize();

            assertEquals(0.5, PATH.nearestShare(FROM.lerp(TO, 0.5).add(beside)), DELTA);
            assertEquals(0, PATH.nearestShare(FROM.add(1, 0, 0)), DELTA);
            assertEquals(1, PATH.nearestShare(TO.subtract(1, 0, 0)), DELTA);
        }

        @Test
        void aPathsFrameIsLevelSquareToItsLineAndTheSameAlongAllOfIt() {
            Vec3 line = TO.subtract(FROM).normalize();
            Vec3 side = DrinkStream.sideOf(PATH);
            Vec3 across = DrinkStream.acrossOf(PATH);

            assertEquals(0, side.y, DELTA, "the side is level");
            assertEquals(0, side.dot(line), DELTA);
            assertEquals(0, across.dot(line), DELTA);
            assertEquals(0, across.dot(side), DELTA);
            assertEquals(1, side.length(), DELTA);
            assertEquals(1, across.length(), DELTA);
            assertEquals(new Vec3(1, 0, 0), DrinkStream.sideOf(new DrinkStream.Path(FROM, FROM, SEED)));
        }

        @Test
        void aPathLandingOnATrunkSlidesOntoTheTrunksSpineOverItsLastStretch() {
            DrinkStream.Path trunk = new DrinkStream.Path(new Vec3(9, 2.5, 3.5), TO, SEED + 1);
            double joinShare = 0.5;
            Vec3 join = DrinkStream.pointAt(trunk, joinShare, START);
            Vec3 arrival = DrinkStream.flowAt(trunk, joinShare, START);
            Vec3 far = new Vec3(9, 7.5, 3.5);
            Vec3 lift = new Vec3(0, 0.3, 0);
            DrinkStream.Path alone = new DrinkStream.Path(far, join, SEED, arrival);
            DrinkStream.Path landing = new DrinkStream.Path(far, join, SEED, arrival, trunk, joinShare);
            DrinkStream.Path gliding = new DrinkStream.Path(far, join.add(lift), SEED, arrival, trunk, joinShare);
            double reach = DrinkStream.ARRIVAL_REACH;
            double ride = DrinkStream.RIDE;
            double halfSlide = ride + (reach - ride) / 2;
            double stretchStart = 1 - reach / landing.length();
            double midway = 1 - halfSlide / landing.length();
            Vec3 onTrunk = DrinkStream.pointAt(trunk, joinShare - halfSlide / trunk.length(), START);
            Vec3 rideStart = DrinkStream.pointAt(trunk, joinShare - ride / trunk.length(), START);

            assertEquals(DrinkStream.pointAt(alone, stretchStart / 2, START),
                    DrinkStream.pointAt(landing, stretchStart / 2, START), "its own snake before the stretch");
            assertEquals(0, DrinkStream.pointAt(alone, stretchStart, START)
                    .distanceTo(DrinkStream.pointAt(landing, stretchStart, START)), DELTA, "and where it starts");
            assertEquals(0, DrinkStream.pointAt(alone, midway, START).lerp(onTrunk, 0.5)
                    .distanceTo(DrinkStream.pointAt(landing, midway, START)), DELTA,
                    "midway it is half way from its own snake onto the trunk's spine the same distance before the join");
            assertEquals(0, DrinkStream.pointAt(landing, 1 - ride / landing.length(), START).distanceTo(rideStart),
                    DELTA, "a ride before the join it is wholly on the trunk's spine");
            assertEquals(0, DrinkStream.pointAt(landing, 1, START).distanceTo(join), DELTA, "it ends at the join");
            DrinkStream.Path aloneLifted = new DrinkStream.Path(far, join.add(lift), SEED, arrival);
            double midwayGliding = 1 - halfSlide / gliding.length();
            assertEquals(0, DrinkStream.pointAt(aloneLifted, midwayGliding, START).lerp(onTrunk.add(lift), 0.5)
                    .distanceTo(DrinkStream.pointAt(gliding, midwayGliding, START)), DELTA,
                    "a hand end gliding off the spine carries the stretch with it");
            assertEquals(0, DrinkStream.pointAt(gliding, 1, START).distanceTo(join.add(lift)), DELTA);
        }

        @Test
        void theTextureIsLaidAtItsOwnSizeMirroredWithNoSeam() {
            assertEquals(0, DrinkStream.textureAt(0), DELTA);
            assertEquals(1, DrinkStream.textureAt(1), DELTA);
            assertEquals(0, DrinkStream.textureAt(2), DELTA);
            assertEquals(DrinkStream.textureAt(3.999), DrinkStream.textureAt(4.001), 1e-2);
            assertEquals(DrinkStream.textureAt(0.25), DrinkStream.textureAt(-0.25), DELTA);
        }
    }

    @Nested
    class Shape {

        @Test
        void theStreamTapersToAPointAtBothEndsAndIsWholeBetween() {
            assertEquals(1, DrinkStream.taperAt(MIDDLE, 0, LENGTH), DELTA);
            assertEquals(0, DrinkStream.taperAt(0, 0, LENGTH), DELTA);
            assertEquals(0, DrinkStream.taperAt(LENGTH, 0, LENGTH), DELTA);
            assertEquals(0, DrinkStream.taperAt(LENGTH + 1, 0, LENGTH), DELTA);
        }

        /** decision unmake-stream-is-a-shrinking-waterspout */
        @Test
        void theCyclonesPhaseAtOnePlaceOnTheStreamTurnsAboutItsAxisWithTime() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(List.of(STREAMING.pos()), GLOVE);
            DrinkTree.Stream now = DrinkTree.build(List.of(STREAMING), layout, GLOVE, PULL, FLOWING).getFirst();
            DrinkTree.Stream later = DrinkTree.build(List.of(STREAMING), layout, GLOVE, PULL, FLOWING + 1).getFirst();
            int ring = DrinkTree.rings(now).size() / 2;
            double turned = DrinkTree.rings(later).get(ring).spin() - DrinkTree.rings(now).get(ring).spin();
            double expected = DrinkStream.SPIN - DrinkStream.TWIST * DrinkStream.FLOW;

            assertEquals(0, Math.IEEEremainder(turned - expected, 2 * Math.PI), 1e-6,
                    "a tick on, the phase at one ring has turned by the spin less the twist the liquid carried past");
            assertTrue(Math.abs(expected) > TURNING, "so the grooves go round the axis rather than standing still");
            double lobe = DrinkTree.rings(now).get(ring).spin() / DrinkStream.LOBES;
            assertEquals(1, DrinkStream.cycloneAt(lobe, DrinkTree.rings(now).get(ring).spin()), DELTA,
                    "a lobe stands at the radius");
            assertEquals(1 - DrinkStream.SPIN_DEPTH, DrinkStream.cycloneAt(lobe + Math.PI / DrinkStream.LOBES,
                    DrinkTree.rings(now).get(ring).spin()), DELTA, "a groove falls in between lobes");
            assertTrue(DrinkStream.cycloneAt(lobe, DrinkTree.rings(later).get(ring).spin()) < 1,
                    "a tick on, the lobe has turned away from where it stood");
        }

        @Test
        void aRingsPhaseRunsOnAlongThePathByTheTwistOfTheLiquidBetween() {
            DrinkLayout layout = new DrinkLayout();
            layout.place(List.of(STREAMING.pos()), GLOVE);
            List<DrinkStream.Ring> rings = DrinkTree.rings(DrinkTree.build(List.of(STREAMING), layout, GLOVE, PULL,
                    FLOWING).getFirst());

            for (int index = 1; index < rings.size(); index++) {
                assertEquals(DrinkStream.TWIST * (rings.get(index).material() - rings.get(index - 1).material()),
                        rings.get(index).spin() - rings.get(index - 1).spin(), 1e-9, "continuous along the stream");
            }
            assertTrue(Math.abs(rings.getFirst().spin()) <= Math.PI, "the first ring's phase within half a turn");
        }

        @Test
        void aRingFlowsAlongThePathWithItsRadiusAtItsPace() {
            DrinkStream.Ring ring = DrinkStream.ring(PATH, 0.5, START, RADIUS, 0, 0.5, 2 * DrinkStream.FLOW);
            Vec3 flow = DrinkStream.pointAt(PATH, 0.5 + NUDGE, START).subtract(DrinkStream.pointAt(PATH, 0.5 - NUDGE,
                    START));

            assertEquals(0, ring.flow().distanceTo(flow.normalize()), 1e-3);
            assertEquals(1, ring.flow().length(), 1e-6);
            assertEquals(RADIUS, ring.radius(), DELTA);
            assertEquals(2 * DrinkStream.FLOW, ring.speed(), DELTA);
            assertEquals(0, ring.center().distanceTo(DrinkStream.pointAt(PATH, 0.5, START)), DELTA);
        }

        @Test
        void aPathWithAnArrivalLeavesStraightAndLandsAlongIt() {
            Vec3 arrival = new Vec3(0, 0, -1);
            DrinkStream.Path curved = new DrinkStream.Path(FROM, TO, SEED, arrival);
            Vec3 landing = curved.spineAt(1).subtract(curved.spineAt(1 - NUDGE)).normalize();
            Vec3 leaving = curved.spineAt(NUDGE).subtract(curved.spineAt(0)).normalize();

            assertEquals(FROM, curved.spineAt(0));
            assertEquals(TO, curved.spineAt(1));
            assertTrue(landing.dot(arrival) > 0.99, "lands along the arrival, dot " + landing.dot(arrival));
            assertTrue(leaving.dot(TO.subtract(FROM).normalize()) > 0.99, "leaves straight for the end");
            assertEquals(0, DrinkStream.pointAt(curved, 1, START).distanceTo(TO), DELTA);
        }
    }
}
