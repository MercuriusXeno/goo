package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypes;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A label of one goo type goes onto an empty canister or one holding that type
 * alone, and no other (decision label-goes-on-an-empty-or-matching-vessel).
 * StampedGoo stands the goo and water resources.
 */
class GooLabelApplyTest {

    private static final int VOLUME = 300;

    private StampedGoo goo;
    private FluidResource blaze;
    private FluidResource rock;

    @BeforeEach
    void stampGooTypes() {
        goo = new StampedGoo();
        blaze = goo.resource(GooTypes.BLAZE);
        rock = goo.resource(GooTypes.ROCK);
    }

    @AfterEach
    void releaseGooFluids() {
        goo.close();
    }

    @Nested
    class GoesOn {

        @Test
        void anEmptyCanisterTakesALabel() {
            assertTrue(GooLabelRule.canLabel(CanisterFluidContent.EMPTY, CanisterMetadata.EMPTY, GooTypes.BLAZE));
        }

        @Test
        void aCanisterHoldingTheLabelsTypeAloneTakesTheLabel() {
            assertTrue(GooLabelRule.canLabel(CanisterFluidContent.of(blaze, VOLUME), CanisterMetadata.EMPTY,
                    GooTypes.BLAZE));
        }
    }

    /** The same rule over a vat's goo and water, water counting as another fluid. */
    @Nested
    class OnAVat {

        private static final int WATER = 100;

        @Test
        void anEmptyVatOrOneHoldingTheTypeAloneTakesTheLabel() {
            assertTrue(GooLabelRule.canLabelVat(GooContents.EMPTY, 0, null, GooTypes.BLAZE));
            assertTrue(GooLabelRule.canLabelVat(new GooContents(Map.of(GooTypes.BLAZE, VOLUME)), 0, null,
                    GooTypes.BLAZE));
        }

        @Test
        void aVatHoldingAnotherTypeOrBothOrWaterRefusesTheLabel() {
            assertFalse(GooLabelRule.canLabelVat(new GooContents(Map.of(GooTypes.ROCK, VOLUME)), 0, null,
                    GooTypes.BLAZE));
            assertFalse(GooLabelRule.canLabelVat(new GooContents(Map.of(GooTypes.BLAZE, VOLUME, GooTypes.ROCK, VOLUME)),
                    0, null, GooTypes.BLAZE));
            assertFalse(GooLabelRule.canLabelVat(GooContents.EMPTY, WATER, null, GooTypes.BLAZE));
        }

        @Test
        void anAlreadyLabeledVatRefusesASecondLabel() {
            assertFalse(GooLabelRule.canLabelVat(GooContents.EMPTY, 0, GooTypes.BLAZE, GooTypes.BLAZE));
        }
    }

    @Nested
    class IsRefused {

        @Test
        void aCanisterHoldingAnotherTypeRefusesTheLabel() {
            assertFalse(GooLabelRule.canLabel(CanisterFluidContent.of(rock, VOLUME), CanisterMetadata.EMPTY,
                    GooTypes.BLAZE));
        }

        @Test
        void aCanisterHoldingTheTypeBesideAnotherRefusesTheLabel() {
            CanisterFluidContent mixed = CanisterFluidContent.of(blaze, VOLUME).withVolume(rock, VOLUME);
            assertFalse(GooLabelRule.canLabel(mixed, CanisterMetadata.EMPTY, GooTypes.BLAZE));
        }

        @Test
        void aCanisterHoldingWaterRefusesTheLabel() {
            assertFalse(GooLabelRule.canLabel(CanisterFluidContent.of(goo.vanilla(), VOLUME), CanisterMetadata.EMPTY,
                    GooTypes.BLAZE));
        }

        @Test
        void anAlreadyLabeledCanisterRefusesASecondLabel() {
            assertFalse(GooLabelRule.canLabel(CanisterFluidContent.EMPTY,
                    CanisterMetadata.EMPTY.withGooLabel(GooTypes.BLAZE), GooTypes.BLAZE));
        }
    }
}
