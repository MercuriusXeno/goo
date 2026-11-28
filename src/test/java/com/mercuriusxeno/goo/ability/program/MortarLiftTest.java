package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.program.MortarLift.Coat;
import com.mercuriusxeno.goo.ability.program.MortarLift.Grabbed;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The blocks a Grab lifts from behind Mortar: the columns behind the Mortar joined to the grabbed cell on its face
 * and plane, each as deep as its cell's layers, pulled straight out of the coated face, leaving out a column whose
 * way out is blocked.
 * decision grab-lifts-what-contiguous-rock-goo-paints-as-one-unit
 * decision grab-lift-of-mortar-pulls-out-of-the-coated-face
 * decision grab-lift-takes-mortar-on-the-grabbed-face-and-plane
 * decision grab-lift-leaves-out-a-column-that-would-get-stuck
 */
class MortarLiftTest {

    private static final BlockPos TWO_LAYERS = new BlockPos(0, 1, 0);
    private static final BlockPos THREE_LAYERS = TWO_LAYERS.east();
    private static final BlockPos APART = THREE_LAYERS.east(2);
    private static final Set<Direction> ON_A_TOP = Set.of(Direction.DOWN);
    private static final Predicate<BlockPos> EVERY_BLOCK = cell -> true;
    private static final Predicate<BlockPos> NOTHING_IN_THE_WAY = cell -> false;
    private static final int FIELD_WIDE = 20;
    private static final int FIELD_LONG = 15;
    /** The cap the decision states, written out rather than read from MortarLift so a changed cap reads red. */
    private static final int MOST_CELLS_ONE_LIFT_READS = 256;

    @Test
    void touchingCellsSelectColumnsAsDeepAsTheirLayersAndACellApartSelectsNone() {
        Map<BlockPos, Coat> painted = Map.of(TWO_LAYERS, new Coat(2, ON_A_TOP), THREE_LAYERS, new Coat(3, ON_A_TOP),
                APART, new Coat(2, ON_A_TOP));

        Map<BlockPos, List<BlockPos>> columns = MortarLift.columns(onATop(TWO_LAYERS), painted::get, EVERY_BLOCK,
                NOTHING_IN_THE_WAY);

        assertEquals(Map.of(
                TWO_LAYERS, List.of(TWO_LAYERS.below(), TWO_LAYERS.below(2)),
                THREE_LAYERS, List.of(THREE_LAYERS.below(), THREE_LAYERS.below(2), THREE_LAYERS.below(3))), columns);
    }

    @Test
    void aColumnEndsAtTheFirstBlockTheLiftLeaves() {
        Map<BlockPos, Coat> painted = Map.of(THREE_LAYERS, new Coat(3, ON_A_TOP));

        Map<BlockPos, List<BlockPos>> columns = MortarLift.columns(onATop(THREE_LAYERS), painted::get,
                cell -> !cell.equals(THREE_LAYERS.below(2)), NOTHING_IN_THE_WAY);

        assertEquals(Map.of(THREE_LAYERS, List.of(THREE_LAYERS.below())), columns);
    }

    @Test
    void aCellCoatingAWallSelectsTheBlocksBehindTheWallFace() {
        Map<BlockPos, Coat> painted = Map.of(TWO_LAYERS, new Coat(2, Set.of(Direction.WEST)));

        Map<BlockPos, List<BlockPos>> columns = MortarLift.columns(new Grabbed(TWO_LAYERS, Direction.WEST),
                painted::get, EVERY_BLOCK, NOTHING_IN_THE_WAY);

        assertEquals(Map.of(TWO_LAYERS, List.of(TWO_LAYERS.west(), TWO_LAYERS.west(2))), columns);
    }

    @Test
    void aCellCoatingACeilingSelectsTheBlocksAboveItOnePerLayer() {
        Map<BlockPos, Coat> painted = Map.of(THREE_LAYERS, new Coat(3, Set.of(Direction.UP)));

        Map<BlockPos, List<BlockPos>> columns = MortarLift.columns(new Grabbed(THREE_LAYERS, Direction.UP),
                painted::get, EVERY_BLOCK, NOTHING_IN_THE_WAY);

        assertEquals(Map.of(THREE_LAYERS, List.of(THREE_LAYERS.above(), THREE_LAYERS.above(2),
                THREE_LAYERS.above(3))), columns);
    }

    @Test
    void onlyJoinedCellsOnTheGrabbedFaceAndPlaneSelectColumns() {
        BlockPos sameHeight = TWO_LAYERS.east();
        BlockPos oneHigher = TWO_LAYERS.above();
        BlockPos onAWall = TWO_LAYERS.south();
        Map<BlockPos, Coat> painted = Map.of(TWO_LAYERS, new Coat(1, ON_A_TOP), sameHeight, new Coat(1, ON_A_TOP),
                oneHigher, new Coat(1, ON_A_TOP), onAWall, new Coat(1, Set.of(Direction.SOUTH)));

        Map<BlockPos, List<BlockPos>> columns = MortarLift.columns(onATop(TWO_LAYERS), painted::get, EVERY_BLOCK,
                NOTHING_IN_THE_WAY);

        assertEquals(Map.of(TWO_LAYERS, List.of(TWO_LAYERS.below()), sameHeight, List.of(sameHeight.below())),
                columns);
    }

    @Test
    void oneLiftReadsAtMostTwoHundredFiftySixCells() {
        Map<BlockPos, Coat> painted = new HashMap<>();
        for (int x = 0; x < FIELD_WIDE; x++) {
            for (int z = 0; z < FIELD_LONG; z++) {
                painted.put(new BlockPos(x, 1, z), new Coat(1, ON_A_TOP));
            }
        }

        Map<BlockPos, List<BlockPos>> columns = MortarLift.columns(onATop(TWO_LAYERS), painted::get, EVERY_BLOCK,
                NOTHING_IN_THE_WAY);

        assertEquals(MOST_CELLS_ONE_LIFT_READS, columns.size());
    }

    @Test
    void aColumnWhoseWayOutIsBlockedSelectsNothingWhileItsNeighboursLift() {
        BlockPos middle = TWO_LAYERS.east();
        BlockPos far = TWO_LAYERS.east(2);
        Map<BlockPos, Coat> painted = Map.of(TWO_LAYERS, new Coat(2, ON_A_TOP), middle, new Coat(2, ON_A_TOP),
                far, new Coat(2, ON_A_TOP));

        Map<BlockPos, List<BlockPos>> columns = MortarLift.columns(onATop(TWO_LAYERS), painted::get, EVERY_BLOCK,
                cell -> cell.equals(middle.above()));

        assertEquals(Map.of(TWO_LAYERS, List.of(TWO_LAYERS.below(), TWO_LAYERS.below(2)),
                far, List.of(far.below(), far.below(2))), columns);
    }

    @Test
    void aLookStrikingAWallFaceGrabsTheMortarCoatingIt() {
        BlockPos wall = TWO_LAYERS.west();
        Map<BlockPos, Coat> painted = Map.of(TWO_LAYERS, new Coat(2, Set.of(Direction.WEST)));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(wall), Direction.EAST, wall, false);

        assertEquals(new Grabbed(TWO_LAYERS, Direction.WEST), MortarLift.grabbedCell(hit, painted::get));
    }

    private static Grabbed onATop(BlockPos cell) {
        return new Grabbed(cell, Direction.DOWN);
    }
}
