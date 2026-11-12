package com.mercuriusxeno.goo.ability.program;

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
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
