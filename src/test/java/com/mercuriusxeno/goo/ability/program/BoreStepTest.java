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
     * Bore also breaks what is in its vortex: the cone from the glove, the
     * JSON's radius wide there, to the reach on the eye line
     * (decision bore-breaks-what-is-in-the-vortex).
     */
    @Nested
    class TheVortex {

        /** rock_bore.json's vortex radius at the glove. */
        private static final double RADIUS = 0.3;
        private static final double WIDER = 0.6;
        private static final double SLACK = 1e-9;
        /** The right-hand glove for the eye looking east: ahead, to the south and below the eye. */
        private static final Vec3 GLOVE = new Vec3(0.9, 2.32, 0.85);
        /** Beside the eye line's first block, to the glove's side, which the vortex crosses near the glove. */
        private static final BlockPos BESIDE = new BlockPos(1, 2, 1);
        /** Beside the eye line's second block, which only the wider vortex reaches. */
        private static final BlockPos WIDER_ONLY = new BlockPos(2, 2, 1);
        /** Beside the eye line far out, where the vortex has narrowed toward its tip. */
        private static final BlockPos OUTSIDE = new BlockPos(3, 2, 1);
        private final ChannelAim aim = new ChannelAim(REACH_END, null, CONE_DEGREES);
        private final Predicate<BlockPos> breakable = EYE_ROW.or(Set.of(BESIDE, WIDER_ONLY, OUTSIDE)::contains);
        private final Predicate<BlockPos> air = breakable.negate();

        private List<BlockPos> pick(double radius) {
            return BoreStep.progressing(EYE, GLOVE, radius, aim, air, breakable);
        }

        @Test
        void rockBoresJsonCarriesTheVortexRadiusAtTheGlove() {
            BoreStep bore = AbilityJson.decode("rock_bore").behaviors().stream()
                    .filter(BoreStep.class::isInstance).map(BoreStep.class::cast).findFirst().orElseThrow();
            assertEquals(RADIUS, bore.vortexRadius(), SLACK);
        }

        @Test
        void theCodecReadsTheRadiusTheJsonNames() {
            String json = "{\"breaks\": \"goo:bore_breakable\", \"break_ticks\": 5, \"slowdown\": 1.5,"
                    + " \"vortex_radius\": " + WIDER + "}";
            BoreStep bore = BoreStep.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .getOrThrow();
            assertEquals(WIDER, bore.vortexRadius(), SLACK);
        }

        @Test
        void thePickHoldsTheEyeLineAndTheBlockTheVortexCrossesBesideIt() {
            List<BlockPos> pick = pick(RADIUS);
            for (int east = 1; east <= 4; east++) {
                assertTrue(pick.contains(new BlockPos(east, 2, 0)), "the eye line's x " + east + " is out");
            }
            assertTrue(pick.contains(BESIDE), "the block the vortex crosses beside the line is out");
            assertFalse(pick.contains(OUTSIDE), "a block outside the vortex and off the line is in");
            assertFalse(pick.contains(WIDER_ONLY), "a block only a wider vortex reaches is in");
        }

        @Test
        void aWiderVortexPicksABlockTheNarrowerLeavesOut() {
            assertTrue(pick(WIDER).contains(WIDER_ONLY));
        }

        @Test
        @SuppressWarnings("unchecked")
        void aHeldTickHandsTheVortexsBlockToTheGradualBreakTheNearerFaster() {
            ChannelHost host = hostWith(breakable, Set.of());
            GradualBreak gradualBreak = mock(GradualBreak.class);
            when(host.gradualBreak()).thenReturn(gradualBreak);
            when(host.glove()).thenReturn(GLOVE);
            run(host, new BoreStep(BREAKABLE, PACE, RADIUS, List.of(), List.of()));

            ArgumentCaptor<Map<BlockPos, Double>> rates = ArgumentCaptor.forClass(Map.class);
            verify(gradualBreak).progress(rates.capture(), any());
            assertTrue(rates.getValue().getOrDefault(BESIDE, 0.0) > 0, "the vortex's block gains no progress");
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
