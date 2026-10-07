package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Bore's step walks the look from the eye to the stream's reach, breaking
 * the nearest blocks in its tag up to its count, passing air and stopping at
 * the first solid block outside the tag
 * (decision bore-vortex-with-a-worldspace-shake).
 */
class BoreStepTest {

    private static final TagKey<Block> BREAKABLE =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "bore_breakable"));
    private static final Vec3 EYE = new Vec3(0.5, 2.62, 0.5);
    private static final Vec3 REACH_END = new Vec3(4.5, 2.62, 0.5);
    private static final BlockPos FIRST = new BlockPos(1, 2, 0);
    private static final BlockPos SECOND = new BlockPos(2, 2, 0);
    private static final BlockPos THIRD = new BlockPos(3, 2, 0);

    /**
     * A host whose eye line crosses the given breakable and solid blocks, every other block air.
     */
    private static ChannelHost hostWith(Set<BlockPos> breakable, Set<BlockPos> solid) {
        ChannelHost host = mock(ChannelHost.class);
        when(host.kind()).thenReturn(HostKind.PLAYER);
        when(host.channelAim()).thenReturn(Optional.of(new ChannelAim(REACH_END, Double.NEGATIVE_INFINITY)));
        when(host.eye()).thenReturn(EYE);
        when(host.blockIn(any(), any())).thenAnswer(call -> breakable.contains(call.<BlockPos>getArgument(0)));
        when(host.airAt(any())).thenAnswer(call -> {
            BlockPos pos = call.getArgument(0);
            return !breakable.contains(pos) && !solid.contains(pos);
        });
        return host;
    }

    private static void run(ChannelHost host, int count) {
        ProgramBehavior.forHost(List.of(new BoreStep(BREAKABLE, count)), HostKind.PLAYER).tick(host);
    }

    @Nested
    class Breaking {

        @Test
        void breaksOnlyTheNearestCountOfBlocks() {
            ChannelHost host = hostWith(Set.of(FIRST, SECOND, THIRD), Set.of());

            run(host, 2);

            verify(host).breakBlock(FIRST);
            verify(host).breakBlock(SECOND);
            verify(host, never()).breakBlock(THIRD);
        }

        @Test
        void passesAirToTheNextBlock() {
            ChannelHost host = hostWith(Set.of(THIRD), Set.of());

            run(host, 1);

            verify(host).breakBlock(THIRD);
        }

        @Test
        void stopsAtASolidBlockOutsideTheTag() {
            ChannelHost host = hostWith(Set.of(THIRD), Set.of(SECOND));

            run(host, 1);

            verify(host, never()).breakBlock(any());
        }
    }

    @Nested
    class LineWalk {

        @Test
        void visitsEachBlockTheLineCrossesOnceNearestFirst() {
            assertEquals(List.of(new BlockPos(0, 2, 0), FIRST, SECOND, THIRD, new BlockPos(4, 2, 0)),
                    BoreStep.blocksAlong(EYE, REACH_END));
        }

        @Test
        void zeroLengthLineVisitsItsOwnBlock() {
            assertEquals(List.of(new BlockPos(0, 2, 0)), BoreStep.blocksAlong(EYE, EYE));
        }
    }
}
