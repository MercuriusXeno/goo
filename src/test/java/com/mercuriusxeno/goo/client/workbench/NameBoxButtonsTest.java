package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An open name box takes a ✓ and a gold ↺ right of it on its row, hovering Done and Nevermind and running each
 * (decisions open-name-box-takes-a-done-checkmark, open-name-box-takes-a-gold-arrow-that-cancels).
 */
class NameBoxButtonsTest {
    private static final int BOX_RIGHT = 100;
    private static final int LINE_TOP = 20;
    private static final int LINE_HEIGHT = 14;

    @Test
    void aCheckAndAGoldArrowStandRightOfTheBoxHoveringAndRunningDoneAndNevermind() {
        DrawnFrame frame = new DrawnFrame();
        List<String> ran = new ArrayList<>();

        NameBoxButtons.draw(frame.graphics, DrawnFrame.font(), BOX_RIGHT, LINE_TOP, LINE_HEIGHT, frame.targets,
                () -> ran.add("done"), () -> ran.add("nevermind"));

        DrawnFrame.Text done = frame.find("✓");
        DrawnFrame.Text arrow = frame.find("↺");
        assertTrue(done.x() > BOX_RIGHT && arrow.x() > done.x(), done + " " + arrow);
        assertEquals(List.of(done.y(), Palette.GOLD), List.of(arrow.y(), arrow.color()));
        assertEquals(List.of(Optional.of(BuilderOverlays.hintKey("Done")),
                        Optional.of(BuilderOverlays.hintKey("Nevermind"))),
                List.of(frame.targets.hovered(done.x() + 1, done.y() + 1),
                        frame.targets.hovered(arrow.x() + 1, arrow.y() + 1)));
        frame.targets.click(done.x() + 1, done.y() + 1);
        frame.targets.click(arrow.x() + 1, arrow.y() + 1);
        assertEquals(List.of("done", "nevermind"), ran);
    }
}
