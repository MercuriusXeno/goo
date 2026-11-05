package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests where the one builder frame lays a row's value, with a six-pixel mocked font (decision
 * one-builder-frame-on-every-pane).
 */
class BuilderFrameTest {
    private static final GroupsPane.Bounds ROW = new GroupsPane.Bounds(10, 50, 310, 400);

    /**
     * A row with no label and no amount line lays its box at the row's indent from the value's top, its right edge
     * short of the delete x.
     */
    @Test
    void unlabelledRowLaysItsBoxAtTheIndentShortOfTheDelete() {
        Font font = DrawnFrame.font();
        BuilderFrame.Laid laid = BuilderFrame.lay(font, new BuilderFrame.Row(ROW, null, GooAmountGrid.NONE, true));
        int indent = ROW.left() + ControlFlow.INDENT;
        assertEquals(new BuilderFrame.Laid(indent, indent, ROW.top(), ROW.top(),
                ROW.right() - BuilderFrame.deleteInset(font)), laid);
    }

    /**
     * A labelled row stands its value past its label, and its box one gap below its amount line.
     */
    @Test
    void labelledRowStandsItsBoxPastTheLabelOneGapBelowTheAmountLine() {
        Font font = DrawnFrame.font();
        GooAmountGrid amount = GooAmountGrid.reduced(Map.of("metal", "3"));
        BuilderFrame.Laid laid = BuilderFrame.lay(font, new BuilderFrame.Row(ROW, "Value:", amount, false));
        int indent = ROW.left() + ControlFlow.INDENT;
        assertEquals(new BuilderFrame.Laid(indent, indent + font.width("Value:") + BuilderFrame.LABEL_GAP, ROW.top(),
                ROW.top() + amount.getHeight(font) + BuilderFrame.AMOUNT_GAP, ROW.right()), laid);
    }
}
