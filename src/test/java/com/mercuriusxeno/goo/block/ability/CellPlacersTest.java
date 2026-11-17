package com.mercuriusxeno.goo.block.ability;

import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The player each goo cell credits: the player who fed the cell, the placer a poured layer carries into the cell
 * it lands in, the last player to place overriding the one before, nobody once the cell is gone, and each cell's
 * placer surviving a save through the codec (decision standing-goo-thing-credits-whoever-placed-it; operator
 * rulings: a poured cell inherits its source's placer, and when two players place the last one overrides).
 */
class CellPlacersTest {

    private static final BlockPos FED = new BlockPos(2, 64, 2);
    private static final BlockPos BESIDE = FED.east();
    private static final UUID FIRST = new UUID(1, 1);
    private static final UUID SECOND = new UUID(2, 2);

    private final CellPlacers placers = new CellPlacers();

    @Nested
    class Placing {

        @Test
        void aFedCellCreditsThePlayerWhoFedIt() {
            placers.place(FED, FIRST);
            assertEquals(Optional.of(FIRST), placers.placerAt(FED));
        }

        @Test
        void theLastPlayerToFeedACellOverridesTheFirst() {
            placers.place(FED, FIRST);
            placers.place(FED, SECOND);
            assertEquals(Optional.of(SECOND), placers.placerAt(FED));
        }

        @Test
        void aGoneCellCreditsNobody() {
            placers.place(FED, FIRST);
            placers.forget(FED);
            assertEquals(Optional.empty(), placers.placerAt(FED));
        }
    }

    @Nested
    class Pouring {

        @Test
        void aPouredLayerCarriesItsCellsPlacerIntoAnOpenCell() {
            placers.place(FED, FIRST);
            placers.carry(FED, BESIDE);
            assertEquals(Optional.of(FIRST), placers.placerAt(BESIDE));
        }

        @Test
        void aPouredLayerOverridesThePlacerOfTheCellItLandsIn() {
            placers.place(FED, FIRST);
            placers.place(BESIDE, SECOND);
            placers.carry(FED, BESIDE);
            assertEquals(Optional.of(FIRST), placers.placerAt(BESIDE));
        }

        @Test
        void aLayerNoPlayerPlacedLeavesTheCellItLandsInCreditingItsPlacer() {
            placers.place(BESIDE, SECOND);
            placers.carry(FED, BESIDE);
            assertEquals(Optional.of(SECOND), placers.placerAt(BESIDE));
        }
    }

    @Test
    void aCellsPlacerSurvivesASaveThroughTheCodec() {
        placers.place(FED, FIRST);
        CellPlacers reloaded = CellPlacers.CODEC.parse(JsonOps.INSTANCE,
                CellPlacers.CODEC.encodeStart(JsonOps.INSTANCE, placers).getOrThrow()).getOrThrow();
        assertEquals(Optional.of(FIRST), reloaded.placerAt(FED));
    }
}
