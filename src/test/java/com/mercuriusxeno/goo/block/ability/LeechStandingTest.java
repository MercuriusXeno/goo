package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the one reading of who stands in Leech goo the drain and the wisps
 * share: a Leech cell filling the block the feet are in, or else one holding
 * the mob up, and none on bare floor.
 * hex-wisps-drift-off-mobs-standing-in-leech-goo
 */
class LeechStandingTest {

    private static final BlockPos FEET = new BlockPos(3, 65, 7);
    private static final BlockPos UNDER_FEET = FEET.below();

    @Test
    void feetInsideALeechCellStandInIt() {
        assertEquals(Optional.of(FEET), LeechStanding.leechCellUnder(FEET, UNDER_FEET, FEET::equals));
    }

    @Test
    void aMobHeldUpByALeechCellStandsInIt() {
        assertEquals(Optional.of(UNDER_FEET), LeechStanding.leechCellUnder(FEET, UNDER_FEET, UNDER_FEET::equals));
    }

    @Test
    void aMobOnBareFloorStandsInNoLeech() {
        assertEquals(Optional.empty(), LeechStanding.leechCellUnder(FEET, UNDER_FEET, pos -> false));
    }
}
