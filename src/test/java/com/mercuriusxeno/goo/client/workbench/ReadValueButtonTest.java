package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the read-only value's button through a drawn frame (decision
 * diagnose-and-correct-the-read-value-pencil-without-its-hint).
 */
class ReadValueButtonTest {
    /**
     * The pencil segment hovers the hint every pencil hovers, and the frame around the value hovers nothing.
     */
    @Test
    void pencilHoversTheRenameHintAndTheFrameNothing() {
        DrawnFrame frame = new DrawnFrame();
        List<ExpressionLines.Run> runs = List.of(new ExpressionLines.Run(ExpressionLines.Kind.TEXT, "12", null));
        ReadValueButton.draw(frame.graphics, DrawnFrame.font(), runs, new ReadValueButton.Place(10, 20, 200,
                () -> { }), new ExpressionLines.Painter(frame.graphics, null, frame.targets, Palette.TEXT,
                Palette.LINK, (link, x, y, width) -> { }), frame.targets);
        DrawnFrame.Text pencil = frame.find(ReadValueButton.PENCIL);
        DrawnFrame.Text value = frame.find("12");
        assertEquals(Optional.ofNullable(BuilderOverlays.hintKey("Rename")),
                frame.targets.hoveredArea(pencil.x() + 1, pencil.y() + 1).map(ClickTargets.HoveredArea::hoverKey),
                "the pencil's hover");
        assertEquals(Optional.empty(), frame.targets.hoveredArea(value.x() + 1, value.y() + 1)
                .map(ClickTargets.HoveredArea::hoverKey), "the frame's hover");
    }

    private static ExpressionLines.Painter plain(DrawnFrame frame) {
        return new ExpressionLines.Painter(frame.graphics, null, frame.targets, Palette.TEXT, Palette.LINK,
                (link, x, y, width) -> { });
    }

    /**
     * A value with no runs reads Not explicitly set as a button with a pencil, and a click on it runs its open
     * action (decision no-value-reads-value-not-explicitly-set-as-a-pencil-button).
     */
    @Test
    void anUnsetValueReadsNotExplicitlySetAsAButtonThatOpens() {
        DrawnFrame frame = new DrawnFrame();
        List<String> ran = new ArrayList<>();
        ReadValueButton.draw(frame.graphics, DrawnFrame.font(), List.of(), new ReadValueButton.Place(10, 20, 200,
                () -> ran.add("open")), plain(frame), frame.targets);
        DrawnFrame.Text unset = frame.find(ReadValueButton.NOT_SET);
        DrawnFrame.Text pencil = frame.find(ReadValueButton.PENCIL);
        assertEquals(10 + ButtonPainter.PADDING, unset.x());
        assertTrue(pencil.x() > unset.x() + DrawnFrame.font().width(unset.text()), pencil + " vs " + unset);
        assertTrue(frame.targets.click(unset.x() + 1, unset.y() + 1));
        assertEquals(List.of("open"), ran);
    }

    /**
     * A read value draws inside a button frame ending at its pencil segment, and a click on the pencil runs its open
     * action (decision read-mode-builder-is-a-button-with-a-pencil-segment).
     */
    @Test
    void aReadValueDrawsFramedShortOfItsPencilWhichOpens() {
        DrawnFrame frame = new DrawnFrame();
        List<String> ran = new ArrayList<>();
        List<ExpressionLines.Run> runs = List.of(new ExpressionLines.Run(ExpressionLines.Kind.TEXT, "12", null));
        ReadValueButton.draw(frame.graphics, DrawnFrame.font(), runs, new ReadValueButton.Place(10, 20, 200,
                () -> ran.add("open")), plain(frame), frame.targets);
        DrawnFrame.Text value = frame.find("12");
        DrawnFrame.Text pencil = frame.find(ReadValueButton.PENCIL);
        int[] box = frame.buttonAround(value);
        assertTrue(box[0] < value.x() && box[2] <= pencil.x() && box[2] > value.x()
                + DrawnFrame.font().width(value.text()), java.util.Arrays.toString(box) + " vs " + pencil);
        assertTrue(frame.targets.click(pencil.x() + 1, pencil.y() + 1));
        assertEquals(List.of("open"), ran);
    }

    /**
     * In a linked read value a shift + left click on a $ref opens that prefab, and a plain left click there runs the
     * button's open action (decision shift-left-click-follows-plain-left-modifies).
     */
    @Test
    void aRefInALinkedValueFollowsOnShiftClickAndOpensOnPlainClick() {
        DrawnFrame frame = new DrawnFrame();
        ManagerActions actions = mock(ManagerActions.class);
        List<String> ran = new ArrayList<>();
        List<ExpressionLines.Run> runs = List.of(new ExpressionLines.Run(ExpressionLines.Kind.LINK, "$wood",
                "wood"));
        ReadValueButton.draw(frame.graphics, DrawnFrame.font(), runs, new ReadValueButton.Place(10, 20, 200,
                () -> ran.add("open")), ExpressionLines.Painter.linked(frame.graphics, mock(ItemIcons.class),
                frame.targets, Palette.TEXT, actions), frame.targets);
        frame.shiftClick("$wood");
        verify(actions).openVariable("wood");
        frame.click("$wood");
        assertEquals(List.of("open"), ran);
    }
}
