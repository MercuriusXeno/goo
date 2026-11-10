package com.mercuriusxeno.goo.client.knownitems;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests the known items menu's header geometry, laid from a frame's width and the target's measured widths with no
 * client: the target column reserved left of a vertical rule, the title and the close button on one row right of it
 * with the search bar beneath, a horizontal rule under both, the grid under the rule
 * (decision menu-header-reserves-the-target).
 */
class HeaderLayoutTest {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;
    /** "Target" at six pixels a character. */
    private static final int LABEL_WIDTH = 36;
    /** A name narrow enough for one line at the smaller scale. */
    private static final int SHORT_NAME = 30;
    /** A name wider than the column at the smaller scale. */
    private static final int LONG_NAME = HeaderLayout.COLUMN_WIDTH + 1;
    /** A frame with nothing listed and no cursor over it. */
    private static final KnownItemsOverlayRenderer.Frame FRAME = new KnownItemsOverlayRenderer.Frame(WIDTH, HEIGHT,
            mock(ItemStack.class), List.of(), "", 0, -1, -1);

    private final HeaderLayout empty = HeaderLayout.of(WIDTH, LABEL_WIDTH, 0);
    private final HeaderLayout set = HeaderLayout.of(WIDTH, LABEL_WIDTH, SHORT_NAME);

    /** The target column stands reserved whether a target is set or not. */
    @Nested
    class ReservedColumn {
        @Test
        void emptyAndSetTargetsHoldTheSameColumn() {
            assertEquals(empty.target().column(), set.target().column());
            assertEquals(empty.target().secondLine(), set.target().secondLine());
        }

        @Test
        void emptyAndSetTargetsHoldTheSameGridTop() {
            assertEquals(empty.gridTop(), set.gridTop());
            assertEquals(empty.gridTop(), KnownItemsOverlayRenderer.Grid.of(FRAME).top());
        }
    }

    /** The header lays as the operator drew it. */
    @Nested
    class AsDrawn {
        @Test
        void targetColumnStandsLeftOfTheVerticalRule() {
            assertTrue(set.target().column().right() <= set.verticalRuleX());
            assertTrue(set.target().icon().left() + set.target().icon().width() <= set.verticalRuleX());
        }

        @Test
        void titleAndCloseButtonShareARowRightOfTheRule() {
            KnownItemsOverlayRenderer.Box close = KnownItemsOverlayRenderer.closeButton(WIDTH);
            assertEquals(close.top(), set.title().top());
            assertTrue(set.title().left() > set.verticalRuleX());
            assertTrue(set.title().right() <= close.left());
        }

        @Test
        void searchBarStandsBeneathTheTitle() {
            assertTrue(set.search().top() > set.title().bottom());
            assertTrue(set.search().left() > set.verticalRuleX());
        }

        @Test
        void horizontalRuleRunsUnderBothColumns() {
            assertTrue(set.horizontalRuleY() > set.target().column().bottom());
            assertTrue(set.horizontalRuleY() > set.search().bottom());
        }

        @Test
        void gridStartsBelowTheHorizontalRule() {
            assertTrue(KnownItemsOverlayRenderer.Grid.of(FRAME).top() > set.horizontalRuleY());
        }
    }

    /** The target's name draws beneath the Target label in a smaller font, a second line reserved for a long one. */
    @Nested
    class TargetName {
        @Test
        void nameLineIsTheSmallerScalesLineHeight() {
            assertEquals(HeaderLayout.NAME_LINE_HEIGHT, set.target().firstLine().height());
            assertTrue(HeaderLayout.NAME_LINE_HEIGHT < HeaderLayout.FONT_LINE_HEIGHT);
        }

        @Test
        void nameStandsBeneathTheLabelAndItsIcon() {
            assertTrue(set.target().firstLine().top() >= set.target().icon().bottom());
            assertTrue(set.target().secondLine().top() >= set.target().firstLine().bottom());
        }

        @Test
        void shortNameLaysOnOneLine() {
            assertEquals(1, set.target().nameLines());
        }

        @Test
        void longNameSplitsOntoTheSecondLine() {
            HeaderLayout spilled = HeaderLayout.of(WIDTH, LABEL_WIDTH, LONG_NAME);
            assertEquals(2, spilled.target().nameLines());
            assertEquals(set.target().secondLine(), spilled.target().secondLine());
        }

        @Test
        void labelStandsBeforeTheIcon() {
            assertTrue(set.target().label().right() < set.target().icon().left());
        }
    }
}
