package com.mercuriusxeno.goo.ability.hole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the opening a hole's mouth is drawn across: the flat rectangle on the face the mouth stands on,
 * spanning exactly the cells of the hole's footprint.
 * decision hole-mouth-opens-three-by-three-and-fits-its-room
 */
class HoleFrameTest {

    @Test
    void aThreeByThreeFloorMouthSpansItsNineTopFaces() {
        List<BlockPos> footprint = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                footprint.add(new BlockPos(4 + x, 64, -2 + z));
            }
        }

        assertEquals(new AABB(3, 65, -3, 6, 65, 0), HoleFrame.openingOn(footprint, Direction.UP));
    }

    @Test
    void aOneByTwoCeilingMouthSpansItsTwoBottomFaces() {
        List<BlockPos> footprint = List.of(new BlockPos(4, 64, -2), new BlockPos(5, 64, -2));

        assertEquals(new AABB(4, 64, -2, 6, 64, -1), HoleFrame.openingOn(footprint, Direction.DOWN));
    }
}
