package com.mercuriusxeno.goo.entity;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A chunk seats only in the grid column it stands over, never drawn across
 * to a hole more than half a block off: held and pushed into the ground over
 * its hole it leans into the hole, and let go it drops straight down where it
 * stands (decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid).
 */
class HoleFitTest {

    /** A chunk two blocks tall: its origin and the block above it. */
    private static final List<BlockPos> TWO_TALL = List.of(BlockPos.ZERO, new BlockPos(0, 1, 0));
    /** Torn from the surface down: a plus of five on top, a stem under its middle. */
    private static final List<BlockPos> PEG = List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
            new BlockPos(0, 0, 1), new BlockPos(0, 0, -1), new BlockPos(0, -1, 0));
    /** Ground of solid blocks up to y 64. */
    private static final int GROUND_TOP = 64;
    /** The two-tall chunk's hole. */
    private static final Set<BlockPos> TWO_TALL_HOLE = Set.of(new BlockPos(5, 63, 0), new BlockPos(5, 64, 0));
    /** The peg's home, the hole's plus cell. */
    private static final BlockPos PEG_HOME = new BlockPos(10, GROUND_TOP, 10);
    private static final Set<BlockPos> PEG_HOLE = Set.of(PEG_HOME, PEG_HOME.east(), PEG_HOME.west(),
            PEG_HOME.south(), PEG_HOME.north(), PEG_HOME.below());

    private static Predicate<BlockPos> groundWithHoles(Set<BlockPos> holes) {
        return cell -> cell.getY() > GROUND_TOP || holes.contains(cell);
    }

    @Test
    void aHeldShapePushedIntoTheGroundOverItsHoleSeatsInTheHole() {
        BlockPos pushedIn = new BlockPos(5, 64, 0);
        assertEquals(Optional.of(new BlockPos(5, 63, 0)),
                HoleFit.fitNear(pushedIn, TWO_TALL, groundWithHoles(TWO_TALL_HOLE)));
    }

    @Test
    void aHeldShapePushedIntoTheGroundACellBesideItsHoleIsNotDrawnAcross() {
        BlockPos aCellWest = new BlockPos(4, 63, 0);
        assertEquals(Optional.empty(), HoleFit.fitNear(aCellWest, TWO_TALL, groundWithHoles(TWO_TALL_HOLE)));
    }

    @Test
    void aPegLetGoOverItsHoleDropsIntoTheHole() {
        assertEquals(Optional.of(PEG_HOME), HoleFit.settleNear(PEG_HOME.above(3), PEG, groundWithHoles(PEG_HOLE)));
    }

    @Test
    void aPegRestingOnItsHolesRimABlockOffStaysWhereItStands() {
        // Its stem in the hole's east arm, its plus over the rim a block east of home.
        BlockPos onTheRim = PEG_HOME.offset(1, 1, 0);
        assertEquals(Optional.of(onTheRim), HoleFit.settleNear(onTheRim, PEG, groundWithHoles(PEG_HOLE)));
    }

    @Test
    void aChunkLetGoWellBesideItsHoleDropsStraightDownWhereItStands() {
        BlockPos besideAndAbove = PEG_HOME.offset(3, 5, 0);
        assertEquals(Optional.of(PEG_HOME.offset(3, 2, 0)),
                HoleFit.settleNear(besideAndAbove, PEG, groundWithHoles(PEG_HOLE)));
    }

    @Test
    void aChunkRestingOverALedgeSettlesWhereItRests() {
        // Two blocks side by side, the east one hanging past the ground's edge at x 5.
        List<BlockPos> pair = List.of(BlockPos.ZERO, new BlockPos(1, 0, 0));
        Predicate<BlockPos> ledge = cell -> cell.getY() > GROUND_TOP || cell.getX() > 4;
        BlockPos resting = new BlockPos(4, GROUND_TOP + 1, 0);
        assertEquals(Optional.of(resting), HoleFit.settleNear(resting, pair, ledge));
    }

    @Test
    void aChunkStandingInABlockRisesUpItsColumnToTheFirstOpenSeat() {
        BlockPos sunk = new BlockPos(20, GROUND_TOP - 1, 0);
        assertEquals(Optional.of(new BlockPos(20, GROUND_TOP + 1, 0)),
                HoleFit.settleNear(sunk, TWO_TALL, groundWithHoles(Set.of())));
    }
}
