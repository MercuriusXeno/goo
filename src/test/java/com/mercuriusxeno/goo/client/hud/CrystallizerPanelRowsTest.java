package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.GooFormat;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Covers the crystal panel's rows: the tier reached as a header, then the forming goo over the dial tier's volume. */
class CrystallizerPanelRowsTest {

    private static List<PanelRow> rows(long crystallized, ChrysmTier knob) {
        return CrystallizerPanelRows.rows(GooTypes.ROCK, crystallized, knob, ChrysmTier::registryPath);
    }

    private static String text(PanelRow row) {
        return row.segments().getFirst().text();
    }

    @Nested
    class GooRow {

        @Test
        void towardAChrysmReadsCrystallizedOverTheChrysmVolume() {
            PanelRow goo = rows(12_000, ChrysmTier.CHRYSM).getLast();

            assertEquals(PanelPainter.gooIcon(GooTypes.ROCK), goo.icon());
            assertEquals(GooFormat.formatAmount(12_000) + " / "
                    + GooFormat.formatAmount(32_000), text(goo));
        }

        @Test
        void towardBuddingChrysmReadsCrystallizedOverTheBuddingVolume() {
            PanelRow goo = rows(500_000, ChrysmTier.BUDDING_CHRYSM).getLast();

            assertEquals(GooFormat.formatAmount(500_000) + " / "
                    + GooFormat.formatAmount(1_000_000), text(goo));
        }
    }

    @Nested
    class TierHeader {

        @Test
        void pastAChrysmNamesTheChrysmAboveTheGooRow() {
            List<PanelRow> rows = rows(40_000, ChrysmTier.BUDDING_CHRYSM);

            assertEquals(2, rows.size());
            assertEquals(ChrysmTier.CHRYSM.registryPath(), text(rows.getFirst()));
        }

        @Test
        void atBuddingChrysmNamesBuddingChrysm() {
            List<PanelRow> rows = rows(1_000_000, ChrysmTier.FLOWERING_CHRYSM);

            assertEquals(2, rows.size());
            assertEquals(ChrysmTier.BUDDING_CHRYSM.registryPath(), text(rows.getFirst()));
        }

        @Test
        void belowAChrysmHoldsTheGooRowAlone() {
            assertEquals(1, rows(12_000, ChrysmTier.CHRYSM).size());
        }
    }
}
