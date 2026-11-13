package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    /**
     * Decision row-amount-grid-fills-the-width-and-wraps-past-it: five amounts the row's width holds stand on one
     * line, so the box stands one line's height and one gap below the value's top.
     */
    @Test
    void amountsTheRowHoldsStandOnOneLine() {
        Font font = DrawnFrame.font();
        List<TooltipLine.GooAmount> five = Collections.nCopies(5, new TooltipLine.GooAmount("metal", "3"));
        GooAmountGrid amount = GooAmountGrid.inRow(new TooltipLine.GooAmounts(five, true));
        GooAmountGrid oneCell = GooAmountGrid.reduced(Map.of("metal", "3"));
        BuilderFrame.Laid laid = BuilderFrame.lay(font, new BuilderFrame.Row(ROW, "Value:", amount, true));
        assertEquals(ROW.top() + oneCell.getHeight(font) + BuilderFrame.AMOUNT_GAP, laid.boxTop());
    }

    private static final int BOX_HEIGHT = 14;

    /**
     * A failing value draws its error as one red line at its box's bottom (decision
     * one-red-error-line-under-every-value).
     */
    @Test
    void aFailingValueDrawsOneRedLineAtItsBoxsBottom() {
        Font font = DrawnFrame.font();
        DrawnFrame frame = new DrawnFrame();
        BuilderFrame.Row row = new BuilderFrame.Row(ROW, null, GooAmountGrid.NONE, true);
        BuilderFrame.draw(frame.graphics, font, row, new BuilderFrame.Value(false, List.of(),
                "Unknown constant $missing", area -> BOX_HEIGHT));
        List<DrawnFrame.Text> red = frame.drawn().stream().filter(text -> text.color() == Palette.DANGER).toList();
        assertEquals(List.of(List.of("Unknown constant $missing", BuilderFrame.lay(font, row).boxTop() + BOX_HEIGHT)),
                red.stream().map(text -> List.<Object>of(text.text(), text.y())).toList());
    }

    /**
     * An error wraps at word breaks into lines that together read the whole error, each within the room given.
     */
    @Test
    void anErrorWrapsAtWordBreaksIntoLinesWithinTheRoom() {
        Font font = DrawnFrame.font();
        String error = "Unknown constant $missing in the leaf row of beetroot";
        int width = 120;
        List<String> lines = BuilderFrame.errorLines(font, error, width);
        assertTrue(lines.size() > 1, lines.toString());
        lines.forEach(line -> assertTrue(font.width(line) <= width, line));
        assertEquals(error, String.join(" ", lines));
    }

    /**
     * An editing row keeps its label and amount line above its box, the box standing the builder panel's overhang
     * below where it reads, short of the delete x (decision one-builder-frame-on-every-pane).
     */
    @Test
    void anEditingRowKeepsItsLabelAndAmountLineAboveItsBox() {
        Font font = DrawnFrame.font();
        DrawnFrame frame = new DrawnFrame();
        GooAmountGrid amount = GooAmountGrid.reduced(Map.of("metal", "3"));
        BuilderFrame.Row row = new BuilderFrame.Row(ROW, "Value:", amount, true);
        List<GroupsPane.Bounds> boxes = new java.util.ArrayList<>();
        BuilderFrame.draw(frame.graphics, font, row, new BuilderFrame.Value(true, List.of(), "", area -> {
            boxes.add(area);
            return BOX_HEIGHT;
        }));
        BuilderFrame.Laid laid = BuilderFrame.lay(font, row);
        GroupsPane.Bounds box = boxes.getFirst();
        assertEquals(List.of(laid.boxTop() + ExpressionBuilderPane.PANEL_ABOVE, ROW.right()
                - BuilderFrame.deleteInset(font)), List.of(box.top(), box.right()));
        assertTrue(frame.find("Value:").y() < box.top() && frame.find("3").y() < box.top(), frame.drawn().toString());
    }
}
