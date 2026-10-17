package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A block bursts into its break particles once, the frame its square lands:
 * none before, its whole count scaled by its volume at the first frame at or
 * past its start, and none for it ever after
 * (decision unmake-drink-is-a-stream-of-break-particles).
 */
class DrinkBurstTest {

    private static final long PICKED = 91;
    private static final long LANDS = 100;
    private static final long DRAINED = 140;
    private static final double BEFORE = 99.5;
    private static final double FIRST_FRAME = 100.2;
    private static final double LATER = 101.7;
    private static final double MUCH_LATER = 120;
    private static final double HALF_VOLUME = Math.sqrt(0.5);

    private static DrinkTree.Block block(BlockPos pos, double scale) {
        return new DrinkTree.Block(pos, Vec3.atCenterOf(pos), scale, PICKED, LANDS, DRAINED);
    }

    @Test
    void aBlockBurstsWholeTheFirstFrameItsSquareHasLandedAndNeverAgain() {
        DrinkBurst bursts = new DrinkBurst();
        DrinkTree.Block stone = block(new BlockPos(3, 4, 5), 1);
        assertEquals(0, bursts.burstAt(stone, BEFORE));
        int burst = bursts.burstAt(stone, FIRST_FRAME);
        assertTrue(burst > 0);
        assertEquals(DrinkBurst.countOf(1), burst);
        assertEquals(0, bursts.burstAt(stone, LATER));
        assertEquals(0, bursts.burstAt(stone, MUCH_LATER));
    }

    @Test
    void aBlockPlacedBackAndDrunkAgainInTheSameDrinkBurstsAgain() {
        DrinkBurst bursts = new DrinkBurst();
        BlockPos pos = new BlockPos(3, 4, 5);
        assertTrue(bursts.burstAt(block(pos, 1), FIRST_FRAME) > 0);
        long again = DRAINED + LANDS;
        DrinkTree.Block placedBack = new DrinkTree.Block(pos, Vec3.atCenterOf(pos), 1, again - (LANDS - PICKED),
                again, again + (DRAINED - LANDS));
        assertEquals(DrinkBurst.countOf(1), bursts.burstAt(placedBack, again));
        assertEquals(0, bursts.burstAt(placedBack, again + 1));
    }

    @Test
    void aBlockOfMoreGooBurstsIntoMoreParticles() {
        DrinkBurst bursts = new DrinkBurst();
        int whole = bursts.burstAt(block(new BlockPos(0, 0, 0), 1), FIRST_FRAME);
        int half = bursts.burstAt(block(new BlockPos(1, 0, 0), HALF_VOLUME), FIRST_FRAME);
        assertTrue(whole > half, whole + " particles for a whole block, " + half + " for half of one");
        assertTrue(half > 0);
    }
}
