package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.ability.hole.HoleDrift;
import com.mercuriusxeno.goo.ability.hole.HoleFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pit behind a hole's mouth: its walls stand inside the footprint's blocks, clear of every
 * outer face of them, so a block face the world draws never shares a plane with a pit wall and the two
 * never fight over which shows; and the carved oval stays within the pit's rim, so every line of sight
 * through the mouth ends on the pit.
 * decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole
 */
class HolePitsTest {

    /** The least a pit wall stands inside the outer face it runs beside, clear of depth fighting. */
    private static final double LEAST_CLEARANCE = 0.01;
    /** Counts of kinds a mouth stores, at and well past the twelve it once showed at most. */
    private static final int[] KIND_COUNTS = {12, 40};

    @Test
    void aCeilingPitOnAOneByTwoOverhangStandsInsideItsBlocks() {
        assertPitInside(List.of(new BlockPos(4, 64, -2), new BlockPos(5, 64, -2)), Direction.DOWN);
    }

    @Test
    void aFloorPitOnAThreeByThreeStandsInsideItsBlocks() {
        assertPitInside(threeByThreeFloor(), Direction.UP);
    }

    /**
     * Half open, the carved oval spans half the full oval while the items keep their full drift places, so
     * some item lies past the oval's edge, behind the face plane, where the uncarved block face hides it.
     * decision hole-seals-over-items-of-fixed-scale
     */
    @Test
    void halfOpenTheOvalAloneShrinksAndSealsOverAnItem() {
        float halfOpen = 0.5F;
        float moment = 137.5F;
        List<BlockPos> footprint = threeByThreeFloor();
        HolePits.CarveSpans full = HolePits.carveSpansOf(footprint, Direction.UP);
        HolePits.CarveSpans carved = HolePits.carvedSpansAt(footprint, Direction.UP, halfOpen);
        assertEquals(full.across() / 2, carved.across(), "half-open oval across");
        assertEquals(full.along() / 2, carved.along(), "half-open oval along");
        AABB rim = HoleFrame.pitOf(footprint, Direction.UP).rim();
        for (int kinds : KIND_COUNTS) {
            int sealedOver = 0;
            for (int index = 0; index < kinds; index++) {
                Vec3 at = HoleDrift.placementAt(index, kinds, moment, rim.getXsize() / 2, rim.getZsize() / 2, halfOpen)
                        .offset();
                double reach = Math.pow(at.x() / carved.across(), 2) + Math.pow(at.z() / carved.along(), 2);
                if (reach > 1 && at.y() < 0) {
                    sealedOver++;
                }
            }
            assertTrue(sealedOver > 0, "no item of " + kinds + " lies past the half-open oval " + carved);
        }
    }

    private static List<BlockPos> threeByThreeFloor() {
        List<BlockPos> footprint = new java.util.ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                footprint.add(new BlockPos(4 + x, 64, -2 + z));
            }
        }
        return footprint;
    }

    private static void assertPitInside(List<BlockPos> footprint, Direction face) {
        AABB blocks = footprint.stream().map(AABB::new).reduce(AABB::minmax).orElseThrow();
        HoleFrame.Pit pit = HoleFrame.pitOf(footprint, face);
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
        HolePits.CarveSpans carve = HolePits.carveSpansOf(footprint, face);
        double rimAcross = pit.rim().getXsize() / 2;
        double rimAlong = pit.rim().getZsize() / 2;
        assertTrue(carve.across() <= rimAcross && carve.along() <= rimAlong, face + " carve reaches " + carve
                + " past the pit rim's half spans " + rimAcross + " by " + rimAlong);
    }
}
