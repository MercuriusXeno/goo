package com.mercuriusxeno.goo.entity;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A held chunk pushed into the ground finds a hollow its shape fits within a
 * block of the aim, so it can be slotted back where it came from; with no
 * fitting hollow in range it stays on the aim
 * (decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid).
 */
class HoleFitTest {

    /** A chunk two blocks tall: its origin and the block above it. */
    private static final List<BlockPos> TWO_TALL = List.of(BlockPos.ZERO, new BlockPos(0, 1, 0));
    /** Ground of solid blocks up to y 64. */
    private static final int GROUND_TOP = 64;
    /** The aim pushed into the ground a cell west of the hole. */
    private static final BlockPos AIM = new BlockPos(4, 63, 0);

    private static Predicate<BlockPos> groundWithHoles(Set<BlockPos> holes) {
        return cell -> cell.getY() > GROUND_TOP || holes.contains(cell);
    }

    @Test
    void aShapePushedIntoTheGroundBesideItsHoleSeatsInTheHole() {
        Set<BlockPos> hole = Set.of(new BlockPos(5, 63, 0), new BlockPos(5, 64, 0));
        Optional<BlockPos> seat = HoleFit.fitNear(AIM, TWO_TALL, groundWithHoles(hole));
        assertEquals(Optional.of(new BlockPos(5, 63, 0)), seat);
    }

    @Test
    void aCrossShapedPegRestingOnItsHolesRimSettlesIntoTheHole() {
        // Torn from the surface down: a plus of five on top, a stem under its middle.
        List<BlockPos> peg = List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
                new BlockPos(0, 0, 1), new BlockPos(0, 0, -1), new BlockPos(0, -1, 0));
        BlockPos home = new BlockPos(10, GROUND_TOP, 10);
        Set<BlockPos> hole = Set.of(home, home.east(), home.west(), home.south(), home.north(), home.below());
        // Its stem stood on the rim a block east, the plus two blocks up.
        BlockPos onTheRim = home.offset(1, 2, 0);
        assertEquals(Optional.of(home), HoleFit.fitNear(onTheRim, peg, groundWithHoles(hole), HoleFit.SETTLE_RADIUS));
    }

    @Test
    void withNoHollowInRangeTheHoldStaysOnTheAim() {
        Set<BlockPos> farHole = Set.of(new BlockPos(9, 63, 0), new BlockPos(9, 64, 0));
        assertEquals(Optional.empty(), HoleFit.fitNear(AIM, TWO_TALL, groundWithHoles(farHole)));
    }
}
