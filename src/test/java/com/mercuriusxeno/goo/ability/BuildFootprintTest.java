package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A Build's footprint covers the rectangle between its corners (decision spire-rips-walls-and-platforms). */
class BuildFootprintTest {

    @Test
    void theGroundCellsCoverTheRectangleBetweenTheCorners() {
        BuildFootprint footprint = new BuildFootprint(new BlockPos(2, 64, 5), new BlockPos(0, 64, 4), 1);

        assertEquals(6, footprint.groundCells().size());
        assertEquals(new BlockPos(0, 64, 4), footprint.min());
        assertEquals(new BlockPos(2, 64, 5), footprint.max());
    }
}
