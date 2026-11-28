package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bore's step drills the breakable blocks at its point, where the eye line
 * meets the first breakable block, and the point moves deeper as they break,
 * each over several ticks at its JSON's pace; a solid block outside its tag
 * on the eye line stops the bore there (decisions
 * bore-breaks-the-block-at-the-point-and-its-surroundings,
 * bore-drills-the-eye-line-with-steady-progress, bore-breaks-what-is-in-the-vortex).
 */
class BoreStepTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "bore_breakable"));
    /** An eye in the block (0, 2, 0) looking east along x to a reach of four. */
    private static final Vec3 EYE = new Vec3(0.5, 2.62, 0.5);
    private static final Vec3 REACH_END = new Vec3(4.5, 2.62, 0.5);
    private static final double CONE_DEGREES = 15;
    /** A cone wide enough to hold blocks beside the eye line, which the line alone never crosses. */
    private static final double WIDE_CONE_DEGREES = 60;
    private static final BlockPos FIRST_ON_LINE = new BlockPos(1, 2, 0);
    private static final BlockPos SECOND_ON_LINE = FIRST_ON_LINE.east();
    private static final BreakPace PACE = new BreakPace(5, 1.5);
    private static final int HELD_TICKS = 60;
    private static final Predicate<BlockPos> EYE_ROW = pos -> pos.getX() >= 1 && pos.getY() == 2 && pos.getZ() == 0;

    /**
     * A host aiming down the cone given, whose blocks are breakable where the
     * predicate says until broken, solid outside the tag where solid says, and
     * air elsewhere.
     */
    private static ChannelHost hostWith(Predicate<BlockPos> breakable, Set<BlockPos> solid, double coneDegrees) {
        ChannelHost host = mock(ChannelHost.class);
        Set<BlockPos> broken = new HashSet<>();
        Predicate<BlockPos> standing = pos -> breakable.test(pos) && !broken.contains(pos);
        GradualBreak gradualBreak = new GradualBreak();
        when(host.kind()).thenReturn(HostKind.PLAYER);
        when(host.channelAim()).thenReturn(Optional.of(new ChannelAim(REACH_END, null, coneDegrees)));
        when(host.eye()).thenReturn(EYE);
        when(host.gradualBreak()).thenReturn(gradualBreak);
        when(host.blockIn(any(), any())).thenAnswer(call -> standing.test(call.getArgument(0)));
        when(host.airAt(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return !standing.test(pos) && !solid.contains(pos);
        });
        doAnswer(call -> broken.add(call.getArgument(0))).when(host).breakBlockQuietly(any());
        return host;
    }

    private static ChannelHost hostWith(Predicate<BlockPos> breakable, Set<BlockPos> solid) {
        return hostWith(breakable, solid, CONE_DEGREES);
    }

    private static void run(ChannelHost host, BoreStep bore) {
        new ProgramBehavior(List.of(bore)).tick(host);
    }

    /**
     * The tick a block breaks on while the bore holds.
     *
     * @return the tick, 1 for the first, or 0 where it stood through the hold
     */
    private static int breakTickOf(BlockPos pos, BreakPace pace) {
        ChannelHost host = hostWith(EYE_ROW, Set.of());
        BoreStep bore = new BoreStep(BREAKABLE, pace);
        for (int held = 1; held <= HELD_TICKS; held++) {
            run(host, bore);
            if (!host.blockIn(pos, BREAKABLE)) {
                return held;
            }
        }
        return 0;
    }

    @Nested
    class ThePace {

        @Test
        void theNearestBlockBreaksOnTheTickTheBreakTicksName() {
            ChannelHost host = hostWith(EYE_ROW, Set.of());
            BoreStep bore = new BoreStep(BREAKABLE, PACE);

            for (int held = 1; held < PACE.breakTicks(); held++) {
                run(host, bore);
            }
            verify(host, never()).breakBlockQuietly(any());

            run(host, bore);
            verify(host).breakBlockQuietly(FIRST_ON_LINE);
            verify(host, times(1)).breakBlockQuietly(any());
        }

        @Test
        void eachNearerBlockBreaksBeforeTheBlockBehindIt() {
            int last = 0;
            for (int east = 1; east <= 4; east++) {
                int tick = breakTickOf(new BlockPos(east, 2, 0), PACE);
                assertTrue(tick > last, "x " + east + " broke on tick " + tick + ", not after tick " + last);
                last = tick;
            }
        }

    }

    @Nested
    class TheEyeLine {

        @Test
        void aBlockBesideTheEyeLineInsideTheConeStandsWhileTheLineBreaks() {
            BlockPos beside = SECOND_ON_LINE.south();
            ChannelHost host = hostWith(EYE_ROW.or(beside::equals), Set.of(), WIDE_CONE_DEGREES);
            BoreStep bore = new BoreStep(BREAKABLE, PACE);

            for (int held = 0; held < HELD_TICKS; held++) {
                run(host, bore);
            }

            for (int east = 1; east <= 4; east++) {
                verify(host).breakBlockQuietly(new BlockPos(east, 2, 0));
            }
            verify(host, never()).breakBlockQuietly(beside);
        }
    }

    /**
     * Bore breaks whatever its drill's cross section intersects: the ellipse
     * where the drill, held at its angle off the look, crosses the plane
     * square to the look, centered on the eye line where it meets the first
     * block, so aiming at a face's middle picks one, near an edge two and
     * near a corner four (decisions bore-breaks-what-the-drill-cross-section-intersects,
     * bore-cross-section-is-where-the-angled-drill-meets-the-look-plane,
     * bore-drill-dimensions-drive-the-visuals-and-the-break,
     * bore-breaks-the-block-at-the-point-and-its-surroundings, bore-breaks-what-is-in-the-vortex).
     */
    @Nested
    class TheCrossSection {

        private static final double SLACK = 1e-9;
        private static final double REACH = 4;
        private static final Vec3 EAST = new Vec3(1, 0, 0);
        /** rock_bore.json's drill: 1 wide at the blob, 2 long, 22.5 degrees off the look, 1 past the plane. */
        private static final BoreDrillShape SHIPPED = new BoreDrillShape(1, 2, 22.5, 1);
        /** An eye where four blocks meet: on the line between y 1 and 2 and between z -1 and 0. */
        private static final Vec3 AT_A_CORNER = new Vec3(0.5, 2.0, 0.0);
        /** An eye in the middle of a block's face: y 2 and z 0. */
        private static final Vec3 AT_A_MIDDLE = new Vec3(0.5, 2.5, 0.5);
        /** Every block east of x 1, a wall square to an eye looking east. */
        private static final Predicate<BlockPos> EVERYTHING = pos -> pos.getX() >= 1;

        private static Set<BlockPos> pickFrom(Vec3 eye, BoreDrillShape shape) {
            ChannelAim aim = new ChannelAim(eye.add(EAST.scale(REACH)), null, CONE_DEGREES);
            return Set.copyOf(BoreStep.progressing(eye, shape, true, aim, EVERYTHING.negate(), EVERYTHING));
        }

        private static Set<BlockPos> cornerBlocks() {
            return Set.of(new BlockPos(1, 1, -1), new BlockPos(1, 1, 0), new BlockPos(1, 2, -1),
                    new BlockPos(1, 2, 0));
        }

        @Test
        void rockBoresJsonCarriesTheDrillsFourDimensions() {
            BoreDrillShape shape = AbilityJson.decode("rock_bore").behaviors().stream()
                    .filter(BoreStep.class::isInstance).map(BoreStep.class::cast).findFirst().orElseThrow().shape();
            assertEquals(1, shape.width(), SLACK);
            assertEquals(2, shape.length(), SLACK);
            assertEquals(22.5, shape.angle(), SLACK);
            assertEquals(1, shape.pastLength(), SLACK);
        }

        @Test
        void aimingAtAFacesMiddlePicksThatOneBlock() {
            assertEquals(Set.of(new BlockPos(1, 2, 0)), pickFrom(AT_A_MIDDLE, SHIPPED));
        }

        @Test
        void aimingPointOhFiveFromAnEdgePicksTheTwo() {
            assertEquals(Set.of(new BlockPos(1, 1, 0), new BlockPos(1, 2, 0)),
                    pickFrom(new Vec3(0.5, 2.05, 0.5), SHIPPED));
        }

        @Test
        void aimingPointOhFiveFromACornerOnBothAxesPicksTheFour() {
            assertEquals(cornerBlocks(), pickFrom(new Vec3(0.5, 2.05, 0.05), SHIPPED));
        }

        /**
         * Aimed 0.05 from where four floor blocks meet on both axes, Bore
         * picks those four at any slant, not the row in front of them
         * (decision bore-breaks-what-the-drill-cross-section-intersects).
         */
        @Test
        void aimingAtAFloorsFourBlockCornerAtASlantPicksTheFour() {
            Predicate<BlockPos> floor = pos -> pos.getY() <= 63;
            Vec3 nearTheCorner = new Vec3(0.05, 64.0, 3.05);
            Set<BlockPos> four = Set.of(new BlockPos(-1, 63, 2), new BlockPos(-1, 63, 3), new BlockPos(0, 63, 2),
                    new BlockPos(0, 63, 3));
            for (double pitch : new double[] {90, 60, 45, 30, 20}) {
                double radians = Math.toRadians(pitch);
                Vec3 look = new Vec3(0, -Math.sin(radians), Math.cos(radians));
                Vec3 eye = nearTheCorner.subtract(look.scale(2.5));
                ChannelAim aim = new ChannelAim(eye.add(look.scale(4.5)), null, CONE_DEGREES);
                assertEquals(four, Set.copyOf(BoreStep.progressing(eye, SHIPPED, true, aim, floor.negate(), floor)),
                        "looking " + pitch + " degrees down");
            }
        }

        /**
         * In a 1 by 2 tunnel, aimed at the wall near its foot or its head,
         * Bore breaks the wall's block and never the tunnel's floor or ceiling.
         */
        @Test
        void inATunnelAimedNearTheWallsFootOrHeadNeitherTheFloorNorTheCeilingBreaks() {
            Predicate<BlockPos> tunnel = pos -> pos.getX() < 3 && pos.getZ() == 0
                    && (pos.getY() == 1 || pos.getY() == 2);
            Predicate<BlockPos> stone = tunnel.negate();
            Vec3 eye = new Vec3(0.5, 2.62, 0.5);
            for (double height : new double[] {1.05, 1.15, 2.85, 2.95}) {
                Vec3 look = new Vec3(3, height, 0.5).subtract(eye).normalize();
                ChannelAim aim = new ChannelAim(eye.add(look.scale(4.5)), null, CONE_DEGREES);
                BlockPos wall = new BlockPos(3, height < 2 ? 1 : 2, 0);
                assertEquals(Set.of(wall), Set.copyOf(BoreStep.progressing(eye, SHIPPED, true, aim, tunnel, stone)),
                        "aimed at the wall " + height + " up");
            }
        }

        @Test
        void aimingPointThreeFromACornerOnBothAxesPicksOne() {
            assertEquals(Set.of(new BlockPos(1, 2, 0)), pickFrom(new Vec3(0.5, 2.3, 0.3), SHIPPED));
        }

        @Test
        void aShorterPastLengthNarrowsThePickAtTheSameAim() {
            Vec3 nearACorner = new Vec3(0.5, 2.15, 0.15);
            assertEquals(cornerBlocks(), pickFrom(nearACorner, SHIPPED));
            assertEquals(Set.of(new BlockPos(1, 2, 0)), pickFrom(nearACorner, new BoreDrillShape(1, 2, 22.5, 0.5)));
        }

        /**
         * Bore drills only up to 3 blocks away, whatever the caster's block
         * reach: a block whose face stands 2.5 out is picked, one 3.5 out is
         * not (operator ruling 2026-11-14).
         */
        @Test
        void aBoreDrillsUpToThreeBlocksAwayAndNoFarther() {
            Delivery delivery = AbilityJson.decode("rock_bore").delivery();
            for (double blockReach : new double[] {4.5, 12.5}) {
                ChannelAim aim = new ChannelAim(AT_A_MIDDLE.add(EAST.scale(delivery.reachingTo(blockReach).range())),
                        null, CONE_DEGREES);
                assertEquals(List.of(new BlockPos(3, 2, 0)), pickOfLone(new BlockPos(3, 2, 0), aim),
                        "2.5 out with a block reach of " + blockReach);
                assertEquals(List.of(), pickOfLone(new BlockPos(4, 2, 0), aim),
                        "3.5 out with a block reach of " + blockReach);
            }
        }

        /**
         * A held tick centers the cut on the crosshair: aimed level at the
         * middle of a wall's upper block, it picks that block alone, never the
         * one under it (operator ruling 2026-11-14).
         */
        @Test
        void aHeldTickCentersTheCutOnTheCrosshair() {
            Predicate<BlockPos> wall = pos -> pos.getX() == 1 && (pos.getY() == 1 || pos.getY() == 2) && pos.getZ() == 0;
            ChannelHost host = hostWith(wall, Set.of());
            GradualBreak gradualBreak = mock(GradualBreak.class);
            when(host.gradualBreak()).thenReturn(gradualBreak);
            run(host, new BoreStep(BREAKABLE, PACE));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<BlockPos, Double>> rates = ArgumentCaptor.forClass(Map.class);
            verify(gradualBreak).progress(rates.capture(), any(), eq(true));
            assertEquals(Set.of(FIRST_ON_LINE), rates.getValue().keySet());
        }

        /** The pick from a face's middle along an aim, with one breakable block standing alone in the air. */
        private static List<BlockPos> pickOfLone(BlockPos lone, ChannelAim aim) {
            return BoreStep.progressing(AT_A_MIDDLE, SHIPPED, true, aim, cell -> !cell.equals(lone), lone::equals);
        }

        @Test
        @SuppressWarnings("unchecked")
        void aHeldTickHandsEveryBlockAtTheCornerToTheGradualBreakAtThePaceTheJsonNames() {
            ChannelHost host = hostWith(EVERYTHING, Set.of());
            when(host.eye()).thenReturn(AT_A_CORNER);
            when(host.channelAim()).thenReturn(Optional.of(
                    new ChannelAim(AT_A_CORNER.add(EAST.scale(REACH)), null, CONE_DEGREES)));
            GradualBreak gradualBreak = mock(GradualBreak.class);
            when(host.gradualBreak()).thenReturn(gradualBreak);
            run(host, new BoreStep(BREAKABLE, PACE));

            ArgumentCaptor<Map<BlockPos, Double>> rates = ArgumentCaptor.forClass(Map.class);
            verify(gradualBreak).progress(rates.capture(), any(), eq(true));
            for (BlockPos picked : List.of(new BlockPos(1, 1, -1), new BlockPos(1, 1, 0), new BlockPos(1, 2, -1),
                    new BlockPos(1, 2, 0))) {
                assertEquals(1.0 / PACE.breakTicks(), rates.getValue().get(picked), SLACK, "at " + picked);
            }
        }
    }

    /**
     * A tube of rays the size of the cross section, the eye line and 8 round
     * the ellipse's rim, sets the depth: the nearest block any ray meets puts
     * the cross section there, and the rays pick nothing themselves
     * (decisions bore-tube-rays-set-the-depth-of-the-cross-section,
     * bore-drill-point-floats-along-the-crosshair-to-the-aimed-block).
     */
    @Nested
    class TheTube {

        private static final BoreDrillShape SHIPPED = BoreDrillShape.SHIPPED;
        private static final Vec3 EAST = new Vec3(1, 0, 0);
        private static final double REACH = 6;
        /** A breakable block two east of the eye, its underside at y 2. */
        private static final BlockPos NEAR = new BlockPos(2, 2, 0);
        /** A breakable wall three blocks behind the near block. */
        private static final Predicate<BlockPos> WALL = pos -> pos.getX() == 5;

        private static Vec3 eyeBelowTheNearBlockBy(double gap) {
            return new Vec3(0.5, NEAR.getY() - gap, 0.5);
        }

        private static Set<BlockPos> pickFrom(Vec3 eye, Predicate<BlockPos> solid, Predicate<BlockPos> breakable) {
            ChannelAim aim = new ChannelAim(eye.add(EAST.scale(REACH)), null, CONE_DEGREES);
            return Set.copyOf(BoreStep.progressing(eye, SHIPPED, true, aim, solid.negate(), breakable));
        }

        @Test
        void aRimRayMeetingANearBlockSetsTheDepthAndTheNearBlockIsPicked() {
            Predicate<BlockPos> rock = WALL.or(NEAR::equals);
            assertEquals(Set.of(NEAR), pickFrom(eyeBelowTheNearBlockBy(0.05), rock, rock));
        }

        @Test
        void aBlockOutsideTheTubeSetsNoDepthAndTheEyeLinesWallBlockIsPicked() {
            Predicate<BlockPos> rock = WALL.or(NEAR::equals);
            assertEquals(Set.of(new BlockPos(5, 1, 0)), pickFrom(eyeBelowTheNearBlockBy(0.35), rock, rock));
        }

        @Test
        void aRimRayStoppedByAnUnbreakableBlockSetsTheDepthAndNothingBehindItIsPicked() {
            Vec3 eye = eyeBelowTheNearBlockBy(0.05);
            Predicate<BlockPos> solid = WALL.or(NEAR::equals);
            assertEquals(Set.of(), pickFrom(eye, solid, WALL));
            Vec3 stop = BoreStep.stopOf(eye, eye.add(EAST.scale(REACH)), SHIPPED, true, solid.negate(), WALL);
            assertEquals(NEAR.getX(), stop.x, 0.01);
        }

        @Test
        void theStopTheDrillDrawsToStandsOnTheEyeLineAtTheNearBlocksDepth() {
            Vec3 eye = eyeBelowTheNearBlockBy(0.05);
            Predicate<BlockPos> rock = WALL.or(NEAR::equals);
            Vec3 stop = BoreStep.stopOf(eye, eye.add(EAST.scale(REACH)), SHIPPED, true, rock.negate(), rock);
            assertEquals(0, stop.distanceTo(new Vec3(NEAR.getX(), eye.y, eye.z)), 0.01);
        }
    }

    @Nested
    class TheStoppingBlock {

        @Test
        @SuppressWarnings("unchecked")
        void aSolidBlockOutsideTheTagOnTheEyeLineStopsTheLine() {
            BlockPos wall = SECOND_ON_LINE;
            ChannelHost host = hostWith(EYE_ROW.and(pos -> !pos.equals(wall)), Set.of(wall));
            BoreStep bore = new BoreStep(BREAKABLE, PACE, List.of(), List.of(mock(Step.class)));

            for (int held = 0; held < HELD_TICKS; held++) {
                run(host, bore);
            }

            verify(host).breakBlockQuietly(FIRST_ON_LINE);
            verify(host, times(1)).breakBlockQuietly(any());
            ArgumentCaptor<List<BlockPos>> struck = ArgumentCaptor.forClass(List.class);
            verify(host, times(HELD_TICKS)).forEachLivingIn(struck.capture(), any(), any());
            assertEquals(Set.of(FIRST_ON_LINE), Set.copyOf(struck.getValue()));
        }

        @Test
        @SuppressWarnings("unchecked")
        void theStrikeRunsAlongTheEyeLineFromTheEyeOutToThePoint() {
            BlockPos aimed = new BlockPos(3, 2, 0);
            ChannelHost host = hostWith(aimed::equals, Set.of());
            run(host, new BoreStep(BREAKABLE, PACE, List.of(), List.of(mock(Step.class))));

            ArgumentCaptor<List<BlockPos>> struck = ArgumentCaptor.forClass(List.class);
            verify(host).forEachLivingIn(struck.capture(), any(), any());
            assertEquals(Set.of(new BlockPos(1, 2, 0), new BlockPos(2, 2, 0), aimed), Set.copyOf(struck.getValue()));
        }
    }

    /**
     * The drill in force swells to the giga drill only while the eye line
     * crosses a Mortar cell holding layers.
     * decision boring-through-rock-goo-gives-a-giga-drill-that-drains-it
     * decision bore-drill-dimensions-drive-the-visuals-and-the-break
     */
    @Nested
    class TheDrillInForce {

        private static final ChannelAim AIM = new ChannelAim(REACH_END, null, CONE_DEGREES);
        private static final Predicate<BlockPos> WALL = pos -> pos.getX() >= 2 && pos.getX() <= 3
                && Math.abs(pos.getY() - 2) <= 2 && Math.abs(pos.getZ()) <= 2;

        private BoreDrillShape drillFor(boolean mortarStands, int layers) {
            Predicate<BlockPos> air = pos -> !WALL.test(pos) && !(mortarStands && pos.equals(FIRST_ON_LINE));
            return BoreStep.drillInForce(EYE, BoreDrillShape.SHIPPED, GigaDrill.SHIPPED, true, AIM, air,
                    pos -> mortarStands && pos.equals(FIRST_ON_LINE) ? layers : 0, WALL).shape();
        }

        @Test
        void anEyeLineThroughMortarHoldingLayersSwellsToTheGigaDrill() {
            assertEquals(GigaDrill.SHIPPED.swell(BoreDrillShape.SHIPPED), drillFor(true, 9));
        }

        @Test
        void anEyeLineThroughAMortarCellHoldingNoLayersKeepsBoresOwnDrill() {
            assertEquals(BoreDrillShape.SHIPPED, drillFor(true, 0));
        }

        @Test
        void anEyeLineThroughNoMortarKeepsBoresOwnDrill() {
            assertEquals(BoreDrillShape.SHIPPED, drillFor(false, 0));
        }
    }
}
