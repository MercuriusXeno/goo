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

    /**
     * The block behind whatever block Bore is damaging progresses at half
     * speed, out of view, so the point moves deeper smoothly
     * (decisions bore-block-behind-progresses-at-half-speed,
     * bore-breaks-the-block-at-the-point-and-its-surroundings).
     */
    @Nested
    class TheBlockBehind {

        private static final BreakPace SHIPPED = shippedBore().pace();
        private static final double SLACK = 1e-9;

        private static BoreStep shippedBore() {
            return AbilityJson.decode("rock_bore").behaviors().stream().filter(BoreStep.class::isInstance)
                    .map(BoreStep.class::cast).findFirst().orElseThrow();
        }

        /** The share of a break each block gains over held ticks, read off what the hold hands a mocked break. */
        @SuppressWarnings("unchecked")
        private static Map<BlockPos, Double> progressAfter(ChannelHost host, BoreStep bore, int ticks) {
            GradualBreak gradualBreak = mock(GradualBreak.class);
            when(host.gradualBreak()).thenReturn(gradualBreak);
            Map<BlockPos, Double> progress = new HashMap<>();
            doAnswer(call -> {
                Map<BlockPos, Double> rates = call.getArgument(0);
                rates.forEach((pos, rate) -> progress.merge(pos, rate, Double::sum));
                return null;
            }).when(gradualBreak).progress(any(), any(), anyBoolean());
            for (int held = 0; held < ticks; held++) {
                run(host, bore);
            }
            return progress;
        }

        @Test
        void theBlockBehindGainsHalfTheAimedBlocksProgressEveryTickBeforeItBreaks() {
            ChannelHost host = hostWith(Set.of(FIRST_ON_LINE, SECOND_ON_LINE)::contains, Set.of());
            BoreStep bore = new BoreStep(BREAKABLE, SHIPPED);
            for (int ticks = 1; ticks < SHIPPED.breakTicks(); ticks++) {
                Map<BlockPos, Double> progress = progressAfter(host, bore, 1);
                assertEquals(progress.get(FIRST_ON_LINE) / 2, progress.getOrDefault(SECOND_ON_LINE, 0.0), SLACK,
                        "on tick " + ticks);
            }
        }

        @Test
        void theBlockBehindEachBlockOfAnEdgePickGainsHalfTheRate() {
            Vec3 atAnEdge = new Vec3(0.5, 2.0, 0.5);
            Predicate<BlockPos> rock = pos -> pos.getX() >= 1 && pos.getX() <= 2 && pos.getZ() == 0
                    && (pos.getY() == 1 || pos.getY() == 2);
            ChannelHost host = hostWith(rock, Set.of());
            when(host.eye()).thenReturn(atAnEdge);
            when(host.channelAim()).thenReturn(Optional.of(
                    new ChannelAim(atAnEdge.add(4, 0, 0), null, CONE_DEGREES)));
            Map<BlockPos, Double> progress = progressAfter(host, new BoreStep(BREAKABLE, SHIPPED, 0.1, List.of(),
                    List.of()), 1);
            double aimedRate = 1.0 / SHIPPED.breakTicks();
            assertEquals(aimedRate, progress.get(new BlockPos(1, 1, 0)), SLACK);
            assertEquals(aimedRate, progress.get(new BlockPos(1, 2, 0)), SLACK);
            assertEquals(aimedRate / 2, progress.getOrDefault(new BlockPos(2, 1, 0), 0.0), SLACK);
            assertEquals(aimedRate / 2, progress.getOrDefault(new BlockPos(2, 2, 0), 0.0), SLACK);
        }

        @Test
        void theBlockBehindKeepsItsProgressAndBreaksSoonerOnceItIsAimedAt() {
            Map<BlockPos, Integer> stages = new HashMap<>();
            ChannelHost host = hostWith(Set.of(FIRST_ON_LINE, SECOND_ON_LINE)::contains, Set.of());
            doAnswer(call -> stages.put(call.getArgument(0), call.getArgument(1))).when(host)
                    .crackBlock(any(), anyInt());
            BoreStep bore = new BoreStep(BREAKABLE, SHIPPED);
            int firstBroke = 0;
            int secondBroke = 0;
            int previous = GradualBreak.CLEARED;
            for (int held = 1; held <= HELD_TICKS && secondBroke == 0; held++) {
                run(host, bore);
                if (firstBroke == 0 && !host.blockIn(FIRST_ON_LINE, BREAKABLE)) {
                    firstBroke = held;
                }
                if (!host.blockIn(SECOND_ON_LINE, BREAKABLE)) {
                    secondBroke = held;
                } else {
                    int stage = stages.getOrDefault(SECOND_ON_LINE, GradualBreak.CLEARED);
                    assertTrue(stage >= previous, "the block behind fell to stage " + stage + " on tick " + held);
                    previous = stage;
                }
            }
            assertTrue(firstBroke > 0 && secondBroke > firstBroke, "broke on " + firstBroke + " and " + secondBroke);
            assertTrue(secondBroke - firstBroke < SHIPPED.breakTicks(), "the block behind took "
                    + (secondBroke - firstBroke) + " ticks once aimed at, not fewer than " + SHIPPED.breakTicks());
        }

        @Test
        void rockBoresJsonSetsTheBlockBehindToHalfSpeed() {
            assertEquals(2, SHIPPED.slowdown(), SLACK);
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
        /** rock_bore.json's drill: 0.7 wide at the blob, 2 long, 22.5 degrees off the look, 0.5 past the plane. */
        private static final BoreDrillShape SHIPPED = new BoreDrillShape(0.7, 2, 22.5, 0.5);
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
            assertEquals(0.7, shape.width(), SLACK);
            assertEquals(2, shape.length(), SLACK);
            assertEquals(22.5, shape.angle(), SLACK);
            assertEquals(0.5, shape.pastLength(), SLACK);
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

        @Test
        void aimingPointOneFiveFromACornerOnBothAxesPicksOne() {
            assertEquals(Set.of(new BlockPos(1, 2, 0)), pickFrom(new Vec3(0.5, 2.15, 0.15), SHIPPED));
        }

        @Test
        void aLongerPastLengthWidensThePickAtTheSameAim() {
            Vec3 nearACorner = new Vec3(0.5, 2.11, 0.11);
            assertEquals(cornerBlocks(), pickFrom(nearACorner, new BoreDrillShape(0.7, 2, 22.5, 1)));
            assertEquals(Set.of(new BlockPos(1, 2, 0)), pickFrom(nearACorner, SHIPPED));
        }

        /**
         * A bore aimed along the look out to the reach its delivery takes for
         * a block reach picks a block inside that reach and leaves one past it
         * (decision bore-reaches-as-far-as-your-block-reach).
         */
        @Test
        void aBorePicksABlockInsideTheBlockReachAndLeavesOnePastIt() {
            Delivery delivery = AbilityJson.decode("rock_bore").delivery();
            for (double blockReach : new double[] {4.5, 6}) {
                BlockPos inside = new BlockPos((int) Math.floor(AT_A_MIDDLE.x + blockReach) - 1, 2, 0);
                BlockPos past = inside.east(2);
                ChannelAim aim = new ChannelAim(AT_A_MIDDLE.add(EAST.scale(delivery.reachingTo(blockReach).range())),
                        null, CONE_DEGREES);
                assertEquals(List.of(inside), pickOfLone(inside, aim), "inside a reach of " + blockReach);
                assertEquals(List.of(), pickOfLone(past, aim), "past a reach of " + blockReach);
            }
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
}
