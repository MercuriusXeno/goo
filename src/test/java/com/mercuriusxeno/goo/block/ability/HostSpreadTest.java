package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A sculk charge crossing Host goo keeps its whole strength, and each Host
 * cell turning the block under it to sculk stands lower by the layers a
 * block of sculk spends
 * (decision sculk-charge-runs-along-host-goo-without-weakening).
 */
class HostSpreadTest {

    /** A charge as a zombie's death starts it, stated here so the test checks the seam rather than echoing it. */
    private static final int ZOMBIES_CHARGE = 5;
    /** The most charge one cursor carries in vanilla. */
    private static final int FULL_CHARGE = 1000;
    private static final int SCULK_LAYERS = 3;

    @Nested
    class Charge {

        @Test
        void aChargeLeavesAHostCellAsStrongAsItEntered() {
            assertEquals(ZOMBIES_CHARGE, HostSpread.chargeOut(ZOMBIES_CHARGE, false));
        }

        @Test
        void aFullChargeLeavesAHostCellFull() {
            assertEquals(FULL_CHARGE, HostSpread.chargeOut(FULL_CHARGE, false));
        }

        /** A charge stranded on the goo, nothing left to turn and no step off it, ends. */
        @Test
        void aStrandedChargeEnds() {
            assertEquals(0, HostSpread.chargeOut(FULL_CHARGE, true));
        }
    }

    /**
     * A death spreads sculk from the Host cell nearest it, where that cell
     * lies within Host's radius
     * (decision mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo).
     */
    @Nested
    class StartingCell {

        private static final int RADIUS = 8;
        private final BlockPos death = new BlockPos(10, 64, 10);
        private final BlockPos nearer = death.offset(2, -1, 0);
        private final BlockPos farther = death.offset(-5, 0, 4);
        private final BlockPos outside = death.offset(RADIUS + 1, 0, 0);

        @Test
        void theNearerCellInsideTheRadiusStartsTheCharge() {
            assertEquals(Optional.of(nearer),
                    HostSpread.startingCell(death, List.of(farther, outside, nearer), RADIUS));
        }

        @Test
        void aCellOutsideTheRadiusStartsNothing() {
            assertEquals(Optional.empty(), HostSpread.startingCell(death, List.of(outside), RADIUS));
        }
    }

    @Nested
    class Layers {

        @Test
        void aCellOverReplaceableGroundSpendsABlockOfSculksLayers() {
            assertEquals(SCULK_LAYERS, HostSpread.layersSpent(15, true, SCULK_LAYERS));
        }

        @Test
        void aCellHoldingExactlyABlockOfSculksLayersSpendsThemAll() {
            assertEquals(SCULK_LAYERS, HostSpread.layersSpent(SCULK_LAYERS, true, SCULK_LAYERS));
        }

        @Test
        void aCellShortOfABlockOfSculksLayersSpendsNothing() {
            assertEquals(0, HostSpread.layersSpent(SCULK_LAYERS - 1, true, SCULK_LAYERS));
        }

        @Test
        void aCellOverGroundSculkCannotReplaceSpendsNothing() {
            assertEquals(0, HostSpread.layersSpent(15, false, SCULK_LAYERS));
        }
    }
}
