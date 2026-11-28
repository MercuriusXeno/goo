package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.program.MortarLift.Coat;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The ground a Grab lifts from under Mortar: the columns under the Mortar contiguous with the grabbed cell, each as
 * deep as its cell's layers, Mortar on a top alone.
 * decision grab-lifts-what-contiguous-rock-goo-paints-as-one-unit
 * decision grab-lift-of-rock-goo-is-always-vertical
 */
class MortarLiftTest {

    private static final BlockPos TWO_LAYERS = new BlockPos(0, 1, 0);
    private static final BlockPos THREE_LAYERS = TWO_LAYERS.east();
    private static final BlockPos APART = THREE_LAYERS.east(2);

    @Test
    void touchingCellsSelectColumnsAsDeepAsTheirLayersAndACellApartSelectsNone() {
        Map<BlockPos, Coat> painted = Map.of(TWO_LAYERS, new Coat(2, true), THREE_LAYERS, new Coat(3, true),
                APART, new Coat(2, true));

        Map<BlockPos, List<BlockPos>> columns = MortarLift.columns(TWO_LAYERS, painted::get, cell -> true);

        assertEquals(Map.of(
                TWO_LAYERS, List.of(TWO_LAYERS.below(), TWO_LAYERS.below(2)),
                THREE_LAYERS, List.of(THREE_LAYERS.below(), THREE_LAYERS.below(2), THREE_LAYERS.below(3))), columns);
    }

    @Test
    void aCellCoatingOnlyAWallsSideSelectsNoColumns() {
        Map<BlockPos, Coat> painted = Map.of(TWO_LAYERS, new Coat(2, false));

        assertEquals(Map.of(), MortarLift.columns(TWO_LAYERS, painted::get, cell -> true));
    }

    @Test
    void aColumnEndsAtTheFirstBlockTheLiftLeaves() {
        Map<BlockPos, Coat> painted = Map.of(THREE_LAYERS, new Coat(3, true));

        Map<BlockPos, List<BlockPos>> columns = MortarLift.columns(THREE_LAYERS, painted::get,
                cell -> !cell.equals(THREE_LAYERS.below(2)));

        assertEquals(Map.of(THREE_LAYERS, List.of(THREE_LAYERS.below())), columns);
    }
}
