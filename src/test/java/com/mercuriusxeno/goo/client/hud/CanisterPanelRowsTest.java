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

        List<PanelRow> rows = CanisterPanelRows.rows(new CanisterHudRenderer.SlotData(content, null, 0));

        assertEquals(List.of(
                PanelPainter.gooRow(GooTypes.BLAZE, GooFormat.formatAmount(BLAZE_VOLUME)),
                PanelPainter.gooRow(GooTypes.ROCK, GooFormat.formatAmount(ROCK_VOLUME)),
                PanelPainter.fillRow(ROCK_VOLUME + BLAZE_VOLUME, ContainerCapacity.canisterCapacity(0))), rows);
    }
}
