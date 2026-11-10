package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.client.workbench.Lettering;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that PanelAmountGrid lays a HUD panel's goo amounts as the workbench lays a tooltip's: ceil(sqrt(count))
 * columns filled row by row, each column as wide as its widest cell, the digits at the workbench's amount scale
 * (decision one-class-lays-every-huds-goo-values). Text is six pixels a character, as PanelPainterMeasureTest
 * measures it.
 */
class PanelAmountGridTest {

    /** Six pixels a character, a monospace stand-in for the font. */
    private static final ToIntFunction<String> SIX_PIXELS_A_CHARACTER = text -> 6 * text.length();
    /** The room a cell's icon and its gap take before the digits. */
    private static final float ICON_AND_GAP = PanelPainter.ICON_SIZE + PanelPainter.ICON_TEXT_GAP;

    private static PanelAmountGrid gridOf(String... amounts) {
        List<PanelAmountGrid.Cell> cells = new ArrayList<>();
        for (String amount : amounts) {
            cells.add(PanelAmountGrid.Cell.amount(GooTypes.ROCK, amount));
        }
        return new PanelAmountGrid(cells);
    }

    /** The column count is max(1, ceil(sqrt(count))) and the rows fill top to bottom. */
    @Nested
    class SquareColumns {
        @Test
        void fiveAmountsLayInThreeColumnsAndTwoRows() {
            PanelAmountGrid grid = gridOf("1", "2", "3", "4", "5");
            assertEquals(3, grid.columns());
            assertEquals(2, grid.rows());
        }

        @Test
        void oneAmountLaysInOneColumn() {
            PanelAmountGrid grid = gridOf("1");
            assertEquals(1, grid.columns());
            assertEquals(1, grid.rows());
        }

        @Test
        void nineAmountsLayInThreeColumnsAndThreeRows() {
            PanelAmountGrid grid = gridOf("1", "2", "3", "4", "5", "6", "7", "8", "9");
            assertEquals(3, grid.columns());
            assertEquals(3, grid.rows());
        }

        /** The fifth of five cells stands in the second column of the second row, one row height down. */
        @Test
        void cellsFillRowByRow() {
            PanelAmountGrid grid = gridOf("1", "2", "3", "4", "5");
            assertEquals(grid.columnWidth(0, SIX_PIXELS_A_CHARACTER) + PanelAmountGrid.COLUMN_GAP,
                    grid.cellLeft(4, SIX_PIXELS_A_CHARACTER));
            assertEquals(PanelPainter.ROW_HEIGHT, grid.cellTop(4));
            assertEquals(2 * PanelPainter.ROW_HEIGHT, grid.height());
        }
    }

    /** Each column is as wide as its widest cell, and the grid is its columns with one gap between each. */
    @Nested
    class ColumnWidths {
        /** Four cells lay in two columns: "1" and "3" in the first, "22222" and "4" in the second. */
        private final PanelAmountGrid grid = gridOf("1", "22222", "3", "4");

        @Test
        void eachColumnTakesItsWidestCell() {
            assertEquals(grid.cellWidth(0, SIX_PIXELS_A_CHARACTER), grid.columnWidth(0, SIX_PIXELS_A_CHARACTER));
            assertEquals(grid.cellWidth(1, SIX_PIXELS_A_CHARACTER), grid.columnWidth(1, SIX_PIXELS_A_CHARACTER));
            // "22222" is 30 at full size, 22.5 at the amount scale, 23 rounded up
            assertEquals(ICON_AND_GAP + 23, grid.columnWidth(1, SIX_PIXELS_A_CHARACTER));
        }

        @Test
        void gridIsItsColumnsAndOneGapBetweenEach() {
            assertEquals(grid.columnWidth(0, SIX_PIXELS_A_CHARACTER) + PanelAmountGrid.COLUMN_GAP
                    + grid.columnWidth(1, SIX_PIXELS_A_CHARACTER), grid.width(SIX_PIXELS_A_CHARACTER));
        }
    }

    /** Digits letter at the workbench's amount scale, and a floor text holds a cell's width still. */
    @Nested
    class AmountScale {
        @Test
        void digitWidthIsTheFontWidthAtTheAmountScaleRoundedUp() {
            PanelAmountGrid grid = gridOf("500");
            assertEquals((float) Math.ceil(18 * Lettering.AMOUNT_SCALE),
                    grid.digitsWidth(grid.cells().getFirst(), SIX_PIXELS_A_CHARACTER));
            assertEquals(ICON_AND_GAP + 14, grid.cellWidth(0, SIX_PIXELS_A_CHARACTER));
        }

        @Test
        void floorTextHoldsTheCellWidthUnderIt() {
            PanelAmountGrid.Cell floored = new PanelAmountGrid.Cell(PanelPainter.gooIcon(GooTypes.ROCK),
                    List.of(new PanelRow.TextSegment("5", PanelPainter.TEXT_COLOR)), "9.99K / 9.99K");
            PanelAmountGrid grid = new PanelAmountGrid(List.of(floored));
            // the 13-character floor is 78 at full size, 58.5 at the amount scale, 59 rounded up
            assertEquals(ICON_AND_GAP + 59, grid.cellWidth(0, SIX_PIXELS_A_CHARACTER));
        }
    }

    /** The one shrink rule: a panel past its bound scales by bound over height, and never grows. */
    @Nested
    class ShrinkRule {
        @Test
        void panelTwiceItsBoundScalesByHalf() {
            assertEquals(0.5, PanelPainter.shrinkFactor(1.0, 0.5), 1e-9);
        }

        @Test
        void panelUnderItsBoundKeepsItsSize() {
            assertEquals(1, PanelPainter.shrinkFactor(0.25, 0.5));
        }
    }
}
