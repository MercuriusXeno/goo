package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
