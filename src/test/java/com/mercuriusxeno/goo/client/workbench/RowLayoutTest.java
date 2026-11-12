package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the row arithmetic every listing pane draws from: rows holding a sprite step 18px, and a separator stands
 * between consecutive entries and after none of the last (decisions sprites-stay-16px-and-rows-tighten-to-18px,
 * entries-draw-line-separators).
 */
class RowLayoutTest {
    private static final int TOP = 40;
    private static final int ROWS = 4;

    /**
     * Items rows and Groups cards hold a 16px sprite in an 18px row.
     */
    @Test
    void spriteRowsHoldSixteenInEighteen() {
        assertEquals(16, RowLayout.SPRITE_SIZE);
        for (int rowHeight : List.of(ValuesPane.ROW_HEIGHT, GroupsPane.ROW_HEIGHT, RowLayout.SPRITE_ROW_HEIGHT)) {
            assertEquals(18, rowHeight);
        }
    }

    /**
     * A Groups member card steps the sprite row pitch, so its rule stands the rule's padding below the sprite above
     * (decision rule-takes-padding-above-it).
     */
    @Test
    void memberCardsStepTheSpriteRowPitch() {
        assertEquals(RowLayout.SPRITE_ROW_PITCH, GroupsPane.CARD_HEIGHT);
    }

    /**
     * One set of spacing constants serves every pane: a rule's gap, a button's gap and the builder box's margin
     * (decision spacing-rules-are-one-set-across-every-pane).
     */
    @Test
    void oneSpacingSetNamesTheRuleButtonAndBoxGaps() {
        assertEquals(2, RowLayout.RULE_GAP);
        assertEquals(3, RowLayout.BUTTON_GAP);
        assertEquals(3, RowLayout.BOX_MARGIN);
        assertEquals(RowLayout.RULE_GAP, RowLayout.RULE_PADDING, "a sprite row's rule keeps the rule gap");
    }

    /**
     * Rows holding a sprite step their 18px plus the rule's padding, so the separator above a row stands that padding
     * below the sprite in the row above it, its sprite a pixel below its top (decision rule-takes-padding-above-it).
     */
    @Test
    void separatorStandsThePaddingBelowTheSpriteAbove() {
        assertTrue(RowLayout.RULE_PADDING > 0);
        for (int index = 1; index < ROWS; index++) {
            int above = RowLayout.rowTop(TOP, index - 1, RowLayout.SPRITE_ROW_PITCH);
            int spriteBottom = above + 1 + RowLayout.SPRITE_SIZE;
            int rule = RowLayout.separatorAbove(index, RowLayout.rowTop(TOP, index, RowLayout.SPRITE_ROW_PITCH))
                    .getAsInt();
            assertEquals(RowLayout.RULE_PADDING, rule - spriteBottom);
        }
    }

    /**
     * A separator stands one pixel above every row but the first, so it falls between each pair and after none of
     * the last, whatever the pane's row height: Items, Groups and its cards at 18, a Prefabs block or a Mutators row
     * of its own height.
     *
     * @param rowHeight the pane's row height
     */
    @ParameterizedTest
    @ValueSource(ints = {18, 13, 37})
    void separatorsStandBetweenRowsAndAfterNoneOfTheLast(int rowHeight) {
        List<Integer> separators = new ArrayList<>();
        for (int index = 0; index < ROWS; index++) {
            int rowTop = RowLayout.rowTop(TOP, index, rowHeight);
            OptionalInt separator = RowLayout.separatorAbove(index, rowTop);
            separator.ifPresent(separators::add);
            if (index > 0) {
                int above = RowLayout.rowTop(TOP, index - 1, rowHeight);
                assertTrue(separator.getAsInt() >= above && separator.getAsInt() < rowTop);
            }
        }
        assertEquals(ROWS - 1, separators.size());
        assertTrue(separators.getLast() < RowLayout.rowTop(TOP, ROWS - 1, rowHeight));
    }
}
