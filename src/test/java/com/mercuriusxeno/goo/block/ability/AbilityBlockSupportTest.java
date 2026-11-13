package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A marker rests on the block opposite the face it was placed on, so a sideways marker placed on a wall's south face
 * rests on the wall to its north, not on whatever stands south of it.
 */
class AbilityBlockSupportTest {

    @Test
    void aMarkerPlacedOnASouthFaceRestsOnTheBlockToItsNorth() {
        BlockPos marker = new BlockPos(2, 1, 3);

        assertEquals(marker.north(), AbilityBlock.supportOf(marker, Direction.SOUTH));
    }
}
