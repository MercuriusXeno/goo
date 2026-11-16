package com.mercuriusxeno.goo.block.ability;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
            assertEquals(ZOMBIES_CHARGE, HostSpread.chargeOut(ZOMBIES_CHARGE));
        }

        @Test
        void aFullChargeLeavesAHostCellFull() {
            assertEquals(FULL_CHARGE, HostSpread.chargeOut(FULL_CHARGE));
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
