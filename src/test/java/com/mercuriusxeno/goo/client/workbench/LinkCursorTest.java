package com.mercuriusxeno.goo.client.workbench;

import com.mojang.blaze3d.platform.cursor.CursorType;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests the cursor the manager's frame requests: the green plus while a held drag copies under Ctrl, the pointer hand
 * while shift stands over a link, and the default otherwise, and the plus's image as it ships (decisions
 * ctrl-during-a-rail-drag-shows-a-green-plus-and-drops-a-copy, shift-over-a-link-shows-a-pixel-pointer-hand).
 */
class LinkCursorTest {
    private static final int ALPHA_SHIFT = 24;
    private static final int OPAQUE = 0xFF;
    private static final CursorType HAND = new CursorType("hand", 1L);
    private static final CursorType PLUS = new CursorType("plus", 2L);

    /**
     * A copying hold with Ctrl down requests the plus, the same hold with Ctrl up the default, shift over a link the
     * hand, shift off every link the default, and neither the default; as the screen is removed the window takes the
     * default back.
     */
    @Test
    void frameRequestsThePlusTheHandOrTheDefaultByWhatIsHeld() {
        List<CursorType> onWindow = new ArrayList<>();
        LinkCursor cursor = new LinkCursor(onWindow::add, () -> HAND, () -> PLUS);
        ClickTargets targets = new ClickTargets();
        targets.addFollow(0, 0, 50, 10, () -> { }, null);
        targets.addGrab(60, 0, 10, 10, new CopyingGrab());
        targets.click(65, 5);
        List<CursorType> frames = new ArrayList<>();
        targets.holdControl(true);
        frames.add(cursorOfFrame(cursor, targets, 75));
        targets.holdControl(false);
        frames.add(cursorOfFrame(cursor, targets, 75));
        targets.release();
        targets.holdShift(true);
        frames.add(cursorOfFrame(cursor, targets, 5));
        frames.add(cursorOfFrame(cursor, targets, 75));
        targets.holdShift(false);
        frames.add(cursorOfFrame(cursor, targets, 5));
        assertEquals(List.of(PLUS, CursorType.DEFAULT, HAND, CursorType.DEFAULT, CursorType.DEFAULT), frames);
        cursor.release();
        assertEquals(List.of(CursorType.DEFAULT), onWindow);
    }

    /**
     * The plus ships beside the pointer hand, its corner transparent and the pixel CopyPlus names as its hot spot
     * opaque.
     */
    @Test
    void plusShipsBesideTheHandWithItsHotSpotOnAnOpaquePixel() throws IOException {
        assertEquals(PointerHand.TEXTURE_PATH.replace("pointer_hand", "copy_plus"), CopyPlus.TEXTURE_PATH);
        BufferedImage plus;
        try (InputStream in = LinkCursorTest.class.getResourceAsStream("/assets/goo/" + CopyPlus.TEXTURE_PATH)) {
            assertNotNull(in, CopyPlus.TEXTURE_PATH + " does not ship");
            plus = ImageIO.read(in);
        }
        assertEquals(0, plus.getRGB(0, 0) >>> ALPHA_SHIFT);
        assertEquals(OPAQUE, plus.getRGB(CopyPlus.HOT_X, CopyPlus.HOT_Y) >>> ALPHA_SHIFT);
    }

    // The cursor a frame applies once drawn: the default the frame starts with unless the cursor requested another,
    // as GuiGraphicsExtractor's pending cursor does.
    private static CursorType cursorOfFrame(LinkCursor cursor, ClickTargets targets, double x) {
        CursorType[] pending = {CursorType.DEFAULT};
        cursor.frame(targets, x, 5, requested -> pending[0] = requested);
        return pending[0];
    }

    /**
     * A held grab that copies while Ctrl is down, as a term's rail does.
     */
    private static final class CopyingGrab implements ClickTargets.PointerGrab {
        private boolean control;

        @Override
        public void moved(double x, double y) {
            // The cursor reads only whether the hold copies.
        }

        @Override
        public void released() {
            // Nothing drops.
        }

        @Override
        public void holdControl(boolean held) {
            control = held;
        }

        @Override
        public boolean copies() {
            return control;
        }
    }
}
