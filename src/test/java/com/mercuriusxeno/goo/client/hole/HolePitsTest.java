package com.mercuriusxeno.goo.client.hole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pit behind a hole's mouth: its walls stand inside the footprint's blocks, clear of every
 * outer face of them, so a block face the world draws never shares a plane with a pit wall and the two
 * never fight over which shows.
 * decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole
 */
class HolePitsTest {

    /** The least a pit wall stands inside the outer face it runs beside, clear of depth fighting. */
    private static final double LEAST_CLEARANCE = 0.01;

    @Test
    void aCeilingPitOnAOneByTwoOverhangStandsInsideItsBlocks() {
        assertPitInside(List.of(new BlockPos(4, 64, -2), new BlockPos(5, 64, -2)), Direction.DOWN);
    }

    @Test
    void aFloorPitOnAThreeByThreeStandsInsideItsBlocks() {
        List<BlockPos> footprint = new java.util.ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                footprint.add(new BlockPos(4 + x, 64, -2 + z));
            }
        }
        assertPitInside(footprint, Direction.UP);
    }

    private static void assertPitInside(List<BlockPos> footprint, Direction face) {
        AABB blocks = footprint.stream().map(AABB::new).reduce(AABB::minmax).orElseThrow();
        HolePits.Pit pit = HolePits.pitOf(footprint, face);
        for (AABB plane : new AABB[] {pit.rim(), pit.floor()}) {
            String where = face + " pit plane " + plane + " in blocks " + blocks;
            assertTrue(plane.minX - blocks.minX >= LEAST_CLEARANCE && blocks.maxX - plane.maxX >= LEAST_CLEARANCE,
                    where + " meets a side face across x");
            assertTrue(plane.minZ - blocks.minZ >= LEAST_CLEARANCE && blocks.maxZ - plane.maxZ >= LEAST_CLEARANCE,
                    where + " meets a side face across z");
        }
        double floorY = pit.floor().minY;
        assertTrue(floorY - blocks.minY >= LEAST_CLEARANCE && blocks.maxY - floorY >= LEAST_CLEARANCE,
                face + " pit floor at " + floorY + " meets the blocks' far face in " + blocks);
    }
}
