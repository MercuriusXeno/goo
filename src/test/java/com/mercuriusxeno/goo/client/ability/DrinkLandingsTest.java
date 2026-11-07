package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block begins to peel once, the frame its square lands: not before, at the
 * first frame at or past its start, and never again for that landing
 * (decision unmake-peels-the-block-into-chunks-that-stream-to-the-hand).
 */
class DrinkLandingsTest {

    private static final long PICKED = 91;
    private static final long LANDS = 100;
    private static final long DRAINED = 140;
    private static final double BEFORE = 99.5;
    private static final double FIRST_FRAME = 100.2;
    private static final double LATER = 101.7;
    private static final double MUCH_LATER = 120;

    private static DrinkTree.Block block(BlockPos pos) {
        return new DrinkTree.Block(pos, Vec3.atCenterOf(pos), 1, PICKED, LANDS, DRAINED);
    }

    @Test
    void aBlockPeelsTheFirstFrameItsSquareHasLandedAndNeverAgain() {
        DrinkLandings landings = new DrinkLandings();
        DrinkTree.Block stone = block(new BlockPos(3, 4, 5));
        assertFalse(landings.peelsAt(stone, BEFORE));
        assertTrue(landings.peelsAt(stone, FIRST_FRAME));
        assertFalse(landings.peelsAt(stone, LATER));
        assertFalse(landings.peelsAt(stone, MUCH_LATER));
    }

    @Test
    void aBlockFirstSeenDrainedPeelsNotAtAll() {
        assertFalse(new DrinkLandings().peelsAt(block(new BlockPos(3, 4, 5)), DRAINED));
    }

    @Test
    void aBlockPlacedBackAndDrunkAgainInTheSameDrinkPeelsAgain() {
        DrinkLandings landings = new DrinkLandings();
        BlockPos pos = new BlockPos(3, 4, 5);
        assertTrue(landings.peelsAt(block(pos), FIRST_FRAME));
        long again = DRAINED + LANDS;
        DrinkTree.Block placedBack = new DrinkTree.Block(pos, Vec3.atCenterOf(pos), 1, again - (LANDS - PICKED),
                again, again + (DRAINED - LANDS));
        assertTrue(landings.peelsAt(placedBack, again));
        assertFalse(landings.peelsAt(placedBack, again + 1));
    }
}
