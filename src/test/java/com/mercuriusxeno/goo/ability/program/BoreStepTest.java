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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bore's step cuts a tunnel inside its 15 degree cone and along the eye line,
 * nearest the eye first, its count of breaks a tick, and a solid block outside
 * its tag on the eye line stops the tunnel there
 * (decisions bore-breaks-a-15-degree-cone, bore-vortex-with-a-worldspace-shake).
 */
class BoreStepTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "bore_breakable"));
    /** An eye in the block (0, 2, 0) looking east along x to a reach of four. */
    private static final Vec3 EYE = new Vec3(0.5, 2.62, 0.5);
    private static final Vec3 REACH_END = new Vec3(4.5, 2.62, 0.5);
    private static final double CONE_DEGREES = 15;
    private static final BlockPos FIRST_ON_LINE = new BlockPos(1, 2, 0);
    private static final BlockPos SECOND_ON_LINE = FIRST_ON_LINE.east();
    /** A count past every cell the tunnel can hold, so one tick breaks the whole tunnel. */
    private static final int WHOLE_TUNNEL = 100;
    private static final int HELD_TICKS = 20;

    /**
     * A host whose blocks are breakable where the predicate says until broken,
     * solid outside the tag where solid says, and air elsewhere.
     */
    private static ChannelHost hostWith(Predicate<BlockPos> breakable, Set<BlockPos> solid) {
        ChannelHost host = mock(ChannelHost.class);
        Set<BlockPos> broken = new HashSet<>();
        Predicate<BlockPos> standing = pos -> breakable.test(pos) && !broken.contains(pos);
        when(host.kind()).thenReturn(HostKind.PLAYER);
        when(host.channelAim()).thenReturn(Optional.of(new ChannelAim(REACH_END, null, CONE_DEGREES)));
        when(host.eye()).thenReturn(EYE);
        when(host.blockIn(any(), any())).thenAnswer(call -> standing.test(call.getArgument(0)));
        when(host.airAt(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return !standing.test(pos) && !solid.contains(pos);
        });
        doAnswer(call -> broken.add(call.getArgument(0))).when(host).breakBlock(any());
        return host;
    }

    private static void run(ChannelHost host, BoreStep bore) {
        new ProgramBehavior(List.of(bore)).tick(host);
    }

    @Nested
    class TheCone {

        @Test
        void aBlockBesideTheEyeLineOutsideTheConeStaysWhileTheLineBreaks() {
            BlockPos beside = SECOND_ON_LINE.south();
            ChannelHost host = hostWith(pos -> pos.getX() >= 1, Set.of());

            for (int held = 0; held < HELD_TICKS; held++) {
                run(host, new BoreStep(BREAKABLE, WHOLE_TUNNEL));
            }

            verify(host).breakBlock(SECOND_ON_LINE);
            verify(host, never()).breakBlock(beside);
        }

        @Test
        void theNearestBlockBreaksFirstAndOneTickBreaksItsCount() {
            ChannelHost host = hostWith(pos -> pos.getX() >= 1 && pos.getY() == 2 && pos.getZ() == 0, Set.of());

            run(host, new BoreStep(BREAKABLE, 1));
            verify(host).breakBlock(FIRST_ON_LINE);
            verify(host, times(1)).breakBlock(any());

            run(host, new BoreStep(BREAKABLE, 1));
            verify(host).breakBlock(SECOND_ON_LINE);
            verify(host, times(2)).breakBlock(any());
        }
    }

    @Nested
    class TheStoppingBlock {

        @Test
        @SuppressWarnings("unchecked")
        void aSolidBlockOutsideTheTagOnTheEyeLineStopsTheTunnel() {
            BlockPos wall = SECOND_ON_LINE;
            ChannelHost host = hostWith(pos -> pos.getX() >= 1 && !pos.equals(wall), Set.of(wall));
            BoreStep bore = new BoreStep(BREAKABLE, WHOLE_TUNNEL, List.of(), List.of(mock(Step.class)));

            run(host, bore);

            verify(host).breakBlock(FIRST_ON_LINE);
            verify(host, never()).breakBlock(wall.east());
            verify(host, times(1)).breakBlock(any());
            ArgumentCaptor<List<BlockPos>> struck = ArgumentCaptor.forClass(List.class);
            verify(host).forEachLivingIn(struck.capture(), any(), any());
            assertTrue(struck.getValue().contains(FIRST_ON_LINE));
            assertTrue(struck.getValue().stream().allMatch(cell -> cell.getX() < wall.getX()));
        }
    }
}
