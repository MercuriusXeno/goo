package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A hole's mouth is the 3x3 centred where the blob struck, and where that
 * does not fit, the largest smaller mouth inside that same 3x3, the most
 * centred first, whether or not it covers the struck block itself
 * (decision hole-closes-the-space-to-an-opening-ahead).
 */
class WormholeMouthsTest {

    private static final BlockPos STRUCK = new BlockPos(4, 64, 7);
    /** Across a wall facing north: up, then east. */
    private static final Direction[] ACROSS = {Direction.UP, Direction.EAST};

    @Test
    void theFirstMouthIsTheThreeByThreeCentredOnTheStruckBlock() {
        Set<BlockPos> centred = new HashSet<>();
        for (int up = -1; up <= 1; up++) {
            for (int east = -1; east <= 1; east++) {
                centred.add(STRUCK.above(up).east(east));
            }
        }
        assertEquals(centred, new HashSet<>(Wormhole.mouthsOver(STRUCK, ACROSS).get(0)),
                "The first mouth should be the 3x3 centred on the struck block");
    }

    @Test
    void everyMouthStandsInsideTheCentredThreeByThree() {
        for (List<BlockPos> mouth : Wormhole.mouthsOver(STRUCK, ACROSS)) {
            for (BlockPos cell : mouth) {
                BlockPos off = cell.subtract(STRUCK);
                assertTrue(Math.abs(off.getY()) <= 1 && Math.abs(off.getX()) <= 1 && off.getZ() == 0,
                        "Every mouth should stand inside the 3x3 centred on the struck block, " + mouth + " does not");
            }
        }
    }

    @Test
    void aSmallMouthMayStandBesideTheStruckBlock() {
        assertTrue(Wormhole.mouthsOver(STRUCK, ACROSS).contains(List.of(STRUCK.above())),
                "A 1x1 mouth beside the struck block, inside the centred 3x3, should be among the mouths tried");
    }
}
