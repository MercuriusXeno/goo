package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * Level's step hands the shared gradual break every block of the cursor's
 * 3x3 out from the face the hold began on, up to 3 blocks out, that is in its
 * tag and in reach, each tick, the outermost layer at its JSON's pace and each
 * layer nearer the face slower, never behind the face; a host outside a held
 * channel breaks nothing (decisions flatten-disc-cursor-breaks-above-the-plane,
 * flatten-progresses-its-whole-swath-outermost-fastest,
 * bore-and-flatten-json-carry-two-pace-values).
 */
class LevelStepTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "level_breakable"));
    /** The hold began with the cursor on the top of the block at (10, 63, 0). */
    private static final ChannelAim.FacePlane PLANE = new ChannelAim.FacePlane(new BlockPos(10, 63, 0), Direction.UP);
    private static final Vec3 EYE = new Vec3(8, 65.6, 0.5);
    /** The west face of the block at (10, 64, 0). */
    private static final Vec3 AIM = new Vec3(10, 64.5, 0.5);
    private static final BreakPace PACE = new BreakPace(5, 1.5);
    /** A block of the swath's outermost layer, three out from the face. */
    private static final BlockPos OUTERMOST = new BlockPos(9, 66, -1);
    /** A block of the layer beneath it. */
    private static final BlockPos NEXT_LAYER = OUTERMOST.below();
    private static final int HELD_TICKS = 60;

    /** A host whose blocks are in the tag where the predicate says until broken, every one in reach. */
    private static ChannelHost hostWhere(Predicate<BlockPos> inTag, Optional<ChannelAim> aim, GradualBreak gradual) {
        ChannelHost host = mock(ChannelHost.class);
        Set<BlockPos> broken = new HashSet<>();
        when(host.kind()).thenReturn(HostKind.PLAYER);
        when(host.channelAim()).thenReturn(aim);
        when(host.eye()).thenReturn(EYE);
        when(host.gradualBreak()).thenReturn(gradual);
        when(host.blockIn(any(), any())).thenAnswer(call -> inTag.test(call.getArgument(0))
                && !broken.contains(call.<BlockPos>getArgument(0)));
        when(host.reaches(any())).thenReturn(true);
        doAnswer(call -> broken.add(call.getArgument(0))).when(host).breakBlock(any());
        return host;
    }

    private static ChannelHost hostWhere(Predicate<BlockPos> inTag, Optional<ChannelAim> aim) {
        return hostWhere(inTag, aim, new GradualBreak());
    }

    private static Optional<ChannelAim> aimed() {
        return Optional.of(new ChannelAim(AIM, PLANE));
    }

    private static void run(ChannelHost host, BreakPace pace) {
        ProgramBehavior.forHost(List.of(new LevelStep(BREAKABLE, pace)), HostKind.PLAYER).tick(host);
    }

    /**
     * The tick a block breaks on while Level holds over ground that is all in the tag.
     *
     * @return the tick, 1 for the first, or 0 where it stood through the hold
     */
    private static int breakTickOf(BlockPos pos, BreakPace pace) {
        ChannelHost host = hostWhere(cell -> true, aimed());
        for (int held = 1; held <= HELD_TICKS; held++) {
            run(host, pace);
            if (!host.blockIn(pos, BREAKABLE)) {
                return held;
            }
        }
        return 0;
    }

    @Test
    @SuppressWarnings("unchecked")
    void eachTickHandsTheGradualBreakEverySwathBlock() {
        GradualBreak gradual = mock(GradualBreak.class);
        ChannelHost host = hostWhere(pos -> true, aimed(), gradual);

        run(host, PACE);
        run(host, PACE);

        ArgumentCaptor<Map<BlockPos, Double>> rates = ArgumentCaptor.forClass(Map.class);
        verify(gradual, times(2)).progress(rates.capture(), any());
        Set<BlockPos> swath = Set.copyOf(new ChannelAim(AIM, PLANE).swathOutermostFirst(PLANE.block().above()));
        for (Map<BlockPos, Double> tick : rates.getAllValues()) {
            assertEquals(swath, tick.keySet());
        }
    }

    @Test
    void theOutermostLayerBreaksOnTheTickTheBreakTicksName() {
        ChannelHost host = hostWhere(pos -> true, aimed());

        for (int held = 1; held < PACE.breakTicks(); held++) {
            run(host, PACE);
        }
        verify(host, never()).breakBlock(any());

        run(host, PACE);
        verify(host).breakBlock(OUTERMOST);
        verify(host, never()).breakBlock(NEXT_LAYER);
    }

    @Test
    void doublingTheSlowdownDelaysTheNextLayersBreak() {
        int paced = breakTickOf(NEXT_LAYER, PACE);
        int slower = breakTickOf(NEXT_LAYER, new BreakPace(PACE.breakTicks(), 2 * PACE.slowdown()));

        assertTrue(paced > PACE.breakTicks(), "the next layer broke by tick " + paced);
        assertTrue(slower > paced, "doubling the slowdown broke the next layer on tick " + slower
                + ", not after tick " + paced);
    }

    @Test
    void anOutermostLayerOutsideTheTagLeavesTheFullPaceToTheNextLayerIn() {
        ChannelHost host = hostWhere(pos -> pos.getY() < 66, aimed());

        for (int held = 1; held <= PACE.breakTicks(); held++) {
            run(host, PACE);
        }

        verify(host).breakBlock(NEXT_LAYER);
    }
}
