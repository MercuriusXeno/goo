package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.item.GooLabelCraft.Cell;
import com.mercuriusxeno.goo.item.GooLabelCraft.Craft;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One paper beside one goo crafts a label of the goo's type and costs the goo 1 mB,
 * and nothing turns a label back (decision goo-label-is-paper-plus-one-mb-of-goo).
 */
class GooLabelCraftTest {

    private static final int GRID = 9;
    private static final int VOLUME = 250;

    private static List<Cell> grid(Cell... placed) {
        List<Cell> cells = new ArrayList<>(Collections.nCopies(GRID, Cell.EMPTY));
        for (int i = 0; i < placed.length; i++) {
            cells.set(i * 2, placed[i]);
        }
        return cells;
    }

    @Nested
    class Matches {

        @Test
        void paperBesideGooCraftsALabelOfTheGoosTypeAndLeavesTheGooAtOneMbLess() {
            assertEquals(Optional.of(new Craft(GooTypes.BLAZE, 2, VOLUME - 1)),
                    GooLabelCraft.match(grid(Cell.PAPER, Cell.goo(GooTypes.BLAZE, VOLUME))));
        }

        @Test
        void gooAtOneMbCraftsALabelAndLeavesNoGoo() {
            assertEquals(Optional.of(new Craft(GooTypes.FROST, 0, 0)),
                    GooLabelCraft.match(grid(Cell.goo(GooTypes.FROST, 1), Cell.PAPER)));
        }
    }

    @Nested
    class Refuses {

        @Test
        void gooAtZeroMbMatchesNothing() {
            assertEquals(Optional.empty(), GooLabelCraft.match(grid(Cell.PAPER, Cell.goo(GooTypes.BLAZE, 0))));
        }

        @Test
        void aLabelAloneOrBesidePaperMatchesNothing() {
            assertEquals(Optional.empty(), GooLabelCraft.match(grid(Cell.OTHER)));
            assertEquals(Optional.empty(), GooLabelCraft.match(grid(Cell.OTHER, Cell.PAPER)));
        }

        @Test
        void aSecondPaperOrSecondGooMatchesNothing() {
            assertEquals(Optional.empty(), GooLabelCraft.match(
                    grid(Cell.PAPER, Cell.PAPER, Cell.goo(GooTypes.BLAZE, VOLUME))));
            assertEquals(Optional.empty(), GooLabelCraft.match(
                    grid(Cell.PAPER, Cell.goo(GooTypes.BLAZE, VOLUME), Cell.goo(GooTypes.BLAZE, VOLUME))));
        }

        @Test
        void anUntypedGooMatchesNothing() {
            assertEquals(Optional.empty(), GooLabelCraft.match(grid(Cell.PAPER, Cell.goo(null, VOLUME))));
        }
    }
}
