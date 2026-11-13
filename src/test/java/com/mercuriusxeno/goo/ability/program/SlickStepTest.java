package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.throwing.CoatRay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The share of a slick cell one strike builds is the channeler's speed
 * factor over the slick step's ticks (decision slick-resolves-faster-by-speed-over-unadjusted-speed);
 * a drip's strike on a top feeds the open cell over it, and a strike on a
 * side or on a top that holds no film feeds nothing
 * (decision goop-and-slick-coat-lands-where-each-drip-arc-strikes).
 */
class SlickStepTest {

    private static final int TICKS = 20;
    private static final double UNADJUSTED = 0.2;
    private static final SlickStep SLICK = new SlickStep(TICKS, 1.03, 1.08, 40, 1.0, 0.3);

    @Test
    void aChannelerOnSlickAtTwiceTheirUnadjustedSpeedBuildsTwiceTheShareATick() {
        ChannelHost host = mock(ChannelHost.class);
        when(host.channelerTravelSpeed()).thenReturn(2 * UNADJUSTED);
        when(host.channelerUnadjustedSpeed()).thenReturn(UNADJUSTED);
        when(host.channelerStandsOnSlick()).thenReturn(true);

        assertEquals(2f / TICKS, SLICK.layRate(host), 1e-6f);
    }

    @Nested
    class StrikeFeedsAFilm {

        private static final BlockPos FLOOR = new BlockPos(12, 0, 2);
        private static final BlockPos SLAB = new BlockPos(6, 0, 2);
        private static final BlockPos WALL = new BlockPos(3, 1, 2);
        /** Every open cell takes a film but the one over the slab, whose top holds none. */
        private static final Predicate<BlockPos> TAKES_A_FILM = pos -> !pos.equals(SLAB.above());

        private static CoatRay.BlockStrike strike(BlockPos block, Direction face) {
            return new CoatRay.BlockStrike(Vec3.atCenterOf(block).relative(face, 0.5), block, face);
        }

        @Test
        void aStrikeOnAFloorsTopFeedsTheCellOverIt() {
            assertEquals(Optional.of(FLOOR.above()), SlickStep.cellFedBy(strike(FLOOR, Direction.UP), TAKES_A_FILM));
        }

        @Test
        void aStrikeOnATopHoldingNoFilmFeedsNothing() {
            assertEquals(Optional.empty(), SlickStep.cellFedBy(strike(SLAB, Direction.UP), TAKES_A_FILM));
        }

        @Test
        void aStrikeOnAWallsSideFeedsNothing() {
            assertEquals(Optional.empty(), SlickStep.cellFedBy(strike(WALL, Direction.WEST), TAKES_A_FILM));
        }
    }
}
