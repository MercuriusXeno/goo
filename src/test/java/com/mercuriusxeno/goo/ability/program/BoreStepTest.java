package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bore's step drills the breakable blocks its eye line crosses, whatever the
 * stream's cone, each over several ticks at its JSON's pace, the nearest
 * fastest, and a solid block outside its tag on the eye line stops the line
 * there (decisions bore-drills-the-eye-line-with-steady-progress,
 * bore-breaks-a-15-degree-cone, bore-and-flatten-json-carry-two-pace-values).
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
        doAnswer(call -> broken.add(call.getArgument(0))).when(host).breakBlock(any());
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
            verify(host, never()).breakBlock(any());

            run(host, bore);
            verify(host).breakBlock(FIRST_ON_LINE);
            verify(host, times(1)).breakBlock(any());
        }

        @Test
        void doublingTheSlowdownDelaysTheSecondBlocksBreak() {
            int paced = breakTickOf(SECOND_ON_LINE, PACE);
            int slower = breakTickOf(SECOND_ON_LINE, new BreakPace(PACE.breakTicks(), 2 * PACE.slowdown()));

            assertTrue(paced > PACE.breakTicks(), "the second block broke by tick " + paced);
            assertTrue(slower > paced, "doubling the slowdown broke the second block on tick " + slower
                    + ", not after tick " + paced);
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
                verify(host).breakBlock(new BlockPos(east, 2, 0));
            }
            verify(host, never()).breakBlock(beside);
        }
    }

    /**
     * Bore also breaks what is in its vortex: a cone along the eye line from
     * the eye to the reach, from the JSON's wide near radius to its narrower
     * far radius, never wider than a block, so aiming where four blocks meet
     * drills four, at the edge between two drills two, and at a block's
     * middle drills one (decision bore-breaks-what-is-in-the-vortex).
     */
    @Nested
    class TheVortex {

        /** rock_bore.json's vortex radii at its near and far ends. */
        private static final double NEAR = 0.45;
        private static final double FAR = 0.3;
        private static final double SLACK = 1e-9;
        private static final double REACH = 4;
        private static final Vec3 EAST = new Vec3(1, 0, 0);
        /** An eye where four blocks meet: on the line between y 1 and 2 and between z -1 and 0. */
        private static final Vec3 AT_A_CORNER = new Vec3(0.5, 2.0, 0.0);
        /** An eye on the edge between two blocks: between y 1 and 2, in the middle of z 0. */
        private static final Vec3 AT_AN_EDGE = new Vec3(0.5, 2.0, 0.5);
        /** An eye in the middle of a block's face: y 2 and z 0. */
        private static final Vec3 AT_A_MIDDLE = new Vec3(0.5, 2.5, 0.5);
        /** Above the eye line's first block, which the vortex reaches near its wide mouth. */
        private static final BlockPos ABOVE_THE_MOUTH = new BlockPos(1, 3, 0);
        private static final Predicate<BlockPos> EVERYTHING = pos -> pos.getX() >= 1;

        private static Set<BlockPos> pickFrom(Vec3 eye, double near, double far) {
            ChannelAim aim = new ChannelAim(eye.add(EAST.scale(REACH)), null, CONE_DEGREES);
            return Set.copyOf(BoreStep.progressing(eye, near, far, aim, EVERYTHING.negate(), EVERYTHING));
        }

        private static Set<BlockPos> atDepth(Set<BlockPos> pick, int east) {
            return pick.stream().filter(pos -> pos.getX() == east).collect(java.util.stream.Collectors.toSet());
        }

        @Test
        void rockBoresJsonCarriesTheVortexsNearAndFarRadii() {
            BoreStep bore = AbilityJson.decode("rock_bore").behaviors().stream()
                    .filter(BoreStep.class::isInstance).map(BoreStep.class::cast).findFirst().orElseThrow();
            assertEquals(NEAR, bore.vortexNearRadius(), SLACK);
            assertEquals(FAR, bore.vortexFarRadius(), SLACK);
        }

        @Test
        void theCodecReadsTheRadiiTheJsonNames() {
            String json = "{\"breaks\": \"goo:bore_breakable\", \"break_ticks\": 5, \"slowdown\": 1.5,"
                    + " \"vortex_near_radius\": 0.2, \"vortex_far_radius\": 0.4}";
            BoreStep bore = BoreStep.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .getOrThrow();
            assertEquals(0.2, bore.vortexNearRadius(), SLACK);
            assertEquals(0.4, bore.vortexFarRadius(), SLACK);
        }

        @Test
        void aimingWhereFourBlocksMeetDrillsFourAtEveryDepth() {
            Set<BlockPos> pick = pickFrom(AT_A_CORNER, NEAR, FAR);
            for (int east = 1; east <= 4; east++) {
                assertEquals(Set.of(new BlockPos(east, 1, -1), new BlockPos(east, 1, 0), new BlockPos(east, 2, -1),
                        new BlockPos(east, 2, 0)), atDepth(pick, east), "at x " + east);
            }
        }

        @Test
        void aimingAtTheEdgeBetweenTwoBlocksDrillsTwoAtEveryDepth() {
            Set<BlockPos> pick = pickFrom(AT_AN_EDGE, NEAR, FAR);
            for (int east = 1; east <= 4; east++) {
                assertEquals(Set.of(new BlockPos(east, 1, 0), new BlockPos(east, 2, 0)), atDepth(pick, east),
                        "at x " + east);
            }
        }

        @Test
        void aimingAtABlocksMiddleDrillsOneAtEveryDepth() {
            Set<BlockPos> pick = pickFrom(AT_A_MIDDLE, NEAR, FAR);
            for (int east = 1; east <= 4; east++) {
                assertEquals(Set.of(new BlockPos(east, 2, 0)), atDepth(pick, east), "at x " + east);
            }
        }

        @Test
        void aWiderFarEndPicksABlockTheNarrowerLeavesOut() {
            assertFalse(pickFrom(AT_A_MIDDLE, NEAR, FAR).contains(new BlockPos(4, 3, 0)));
            assertTrue(pickFrom(AT_A_MIDDLE, NEAR, 0.6).contains(new BlockPos(4, 3, 0)));
        }

        @Test
        @SuppressWarnings("unchecked")
        void aHeldTickHandsTheVortexsBlockToTheGradualBreakTheNearerFaster() {
            ChannelHost host = hostWith(EYE_ROW.or(ABOVE_THE_MOUTH::equals), Set.of());
            GradualBreak gradualBreak = mock(GradualBreak.class);
            when(host.gradualBreak()).thenReturn(gradualBreak);
            run(host, new BoreStep(BREAKABLE, PACE, NEAR, FAR, List.of(), List.of()));

            ArgumentCaptor<Map<BlockPos, Double>> rates = ArgumentCaptor.forClass(Map.class);
            verify(gradualBreak).progress(rates.capture(), any());
            assertTrue(rates.getValue().getOrDefault(ABOVE_THE_MOUTH, 0.0) > 0,
                    "the vortex's block gains no progress");
            assertTrue(rates.getValue().get(FIRST_ON_LINE) > rates.getValue().get(new BlockPos(4, 2, 0)),
                    "the nearer block gains no quicker than the farther");
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

            verify(host).breakBlock(FIRST_ON_LINE);
            verify(host, times(1)).breakBlock(any());
            ArgumentCaptor<List<BlockPos>> struck = ArgumentCaptor.forClass(List.class);
            verify(host, times(HELD_TICKS)).forEachLivingIn(struck.capture(), any(), any());
            assertEquals(Set.of(FIRST_ON_LINE), Set.copyOf(struck.getValue()));
        }
    }
}
