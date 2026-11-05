package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the one confirm dialog primitive: the whole screen dimmed, its card centered on the screen and the clicks
 * behind it shielded (decision one-wheel-step-and-one-dialog-primitive).
 */
class DialogTest {
    /**
     * The card centers on the screen, the dim spans the screen in the palette's dialog dim, and the card fills in the
     * palette's dialog card.
     */
    @Test
    void cardCentersOnTheDimmedScreen() {
        DrawnFrame frame = new DrawnFrame();
        GroupsPane.Bounds card = Dialog.open(frame.graphics, frame.targets, 300, 140);
        assertEquals(new GroupsPane.Bounds(150, 130, 450, 270), card);
        assertEquals(Palette.DIALOG_DIM, Dialog.DIM);
        assertEquals(Palette.DIALOG_CARD, Dialog.CARD);
        assertTrue(frame.fillsOf(Dialog.DIM).stream().anyMatch(fill -> fill[2] == DrawnFrame.SCREEN_WIDTH
                && fill[3] == DrawnFrame.SCREEN_HEIGHT && fill[0] == 0 && fill[1] == 0), "the screen dims");
        assertTrue(frame.fillsOf(Dialog.CARD).stream().anyMatch(fill -> fill[0] == 150 && fill[1] == 130
                && fill[2] == 450 && fill[3] == 270), "the card fills");
        assertTrue(frame.targets.click(10, 10), "the shield takes a click behind the card");
    }
}
