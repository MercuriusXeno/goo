package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bore's step cuts a 3x3 tunnel along the look: each slice breaks its ring
 * of eight in turn and its middle last, the nearest slice first, its count
 * of breaks a tick, and a solid block outside its tag on the eye line stops
 * the tunnel there (decision bore-vortex-with-a-worldspace-shake).
 */
class BoreStepTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "bore_breakable"));
    /** An eye in the block (0, 2, 0) looking east along x to a reach of four. */
    private static final Vec3 EYE = new Vec3(0.5, 2.62, 0.5);
    private static final Vec3 REACH_END = new Vec3(4.5, 2.62, 0.5);
    private static final BlockPos FIRST_MIDDLE = new BlockPos(1, 2, 0);
    private static final int SLICE = 9;

    /**
     * A host whose blocks are breakable where the predicate says, solid
     * outside the tag where solid says, and air elsewhere.
     */
    private static ChannelHost hostWith(Predicate<BlockPos> breakable, Set<BlockPos> solid) {
        ChannelHost host = mock(ChannelHost.class);
        when(host.kind()).thenReturn(HostKind.PLAYER);
        when(host.channelAim()).thenReturn(Optional.of(new ChannelAim(REACH_END, Double.NEGATIVE_INFINITY)));
        when(host.eye()).thenReturn(EYE);
        when(host.blockIn(any(), any())).thenAnswer(call -> breakable.test(call.getArgument(0)));
        when(host.airAt(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return !breakable.test(pos) && !solid.contains(pos);
        });
        return host;
    }

    private static void run(ChannelHost host, int count) {
        ProgramBehavior.forHost(List.of(new BoreStep(BREAKABLE, count)), HostKind.PLAYER).tick(host);
    }

    @Nested
    class SliceOrder {

        @Test
        void theRingBreaksInTurnAndTheMiddleLast() {
            List<BlockPos> slice = BoreStep.sliceRingIn(FIRST_MIDDLE, Direction.Axis.X);
            assertEquals(SLICE, slice.size());
            assertEquals(FIRST_MIDDLE, slice.getLast());
            for (BlockPos pos : slice) {
                assertEquals(FIRST_MIDDLE.getX(), pos.getX());
                assertEquals(1, Math.max(Math.abs(pos.getY() - 2), Math.abs(pos.getZ())), 1);
            }
            assertEquals(SLICE, Set.copyOf(slice).size());
        }
    }

    @Nested
    class Breaking {

        @Test
        void oneTickBreaksItsCountFromTheNearestRingAndLeavesTheMiddle() {
            ChannelHost host = hostWith(pos -> pos.getX() >= 1, Set.of());

            run(host, 2);

            List<BlockPos> slice = BoreStep.sliceRingIn(FIRST_MIDDLE, Direction.Axis.X);
            verify(host).breakBlock(slice.get(0));
            verify(host).breakBlock(slice.get(1));
            verify(host, never()).breakBlock(FIRST_MIDDLE);
            verify(host, times(2)).breakBlock(any());
        }

        @Test
        void aSolidBlockOutsideTheTagOnTheEyeLineStopsTheTunnel() {
            BlockPos wall = FIRST_MIDDLE.east();
            ChannelHost host = hostWith(pos -> pos.getX() >= 1 && !pos.equals(wall), Set.of(wall));

            run(host, SLICE * 3);

            verify(host).breakBlock(FIRST_MIDDLE);
            verify(host, never()).breakBlock(wall.east());
            verify(host, times(SLICE)).breakBlock(any());
        }
    }
}
