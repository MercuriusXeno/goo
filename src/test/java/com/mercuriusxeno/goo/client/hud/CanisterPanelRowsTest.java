package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.item.StampedGoo;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The canister HUD panel lists each goo type a canister holds with its volume,
 * dominant first, then the total against capacity (decision
 * canisters-hold-more-than-one-goo-type). StampedGoo stands the goo resources.
 */
class CanisterPanelRowsTest {

    private static final int ROCK_VOLUME = 200;
    private static final int BLAZE_VOLUME = 700;
    /** Three mB of void: the first adds 2, the second 3, the third 4. */
    private static final int VOID_VOLUME = 3;
    private static final int VOID_GROWTH = 9;

    private StampedGoo goo;

    @BeforeEach
    void stampGooTypes() {
        goo = new StampedGoo();
    }

    @AfterEach
    void releaseGooFluids() {
        goo.close();
    }

    @Test
    void aTwoTypeCanisterListsTheDominantTypeFirstThenItsTotal() {
        CanisterFluidContent content = CanisterFluidContent.of(goo.resource(GooTypes.ROCK), ROCK_VOLUME)
                .withVolume(goo.resource(GooTypes.BLAZE), BLAZE_VOLUME);

        List<PanelRow> rows = CanisterPanelRows.rows(new CanisterHudRenderer.SlotData(content, null));

        assertEquals(List.of(
                PanelPainter.gooRow(GooTypes.BLAZE, GooFormat.formatAmount(BLAZE_VOLUME)),
                PanelPainter.gooRow(GooTypes.ROCK, GooFormat.formatAmount(ROCK_VOLUME)),
                PanelPainter.fillRow(ROCK_VOLUME + BLAZE_VOLUME, ContainerCapacity.CANISTER_BASE)), rows);
    }

    /**
     * Void a canister holds reads as an ordinary goo row, and the fill row reads
     * the capacity that void grows (decision each-void-mb-adds-n-plus-one-capacity).
     */
    @Test
    void aCanisterHoldingVoidReadsTheCapacityItsVoidGrows() {
        CanisterFluidContent content = CanisterFluidContent.of(goo.resource(GooTypes.VOID), VOID_VOLUME);

        List<PanelRow> rows = CanisterPanelRows.rows(new CanisterHudRenderer.SlotData(content, null));

        assertEquals(List.of(
                PanelPainter.gooRow(GooTypes.VOID, GooFormat.formatAmount(VOID_VOLUME)),
                PanelPainter.fillRow(VOID_VOLUME, ContainerCapacity.CANISTER_BASE + VOID_GROWTH)), rows);
    }
}
