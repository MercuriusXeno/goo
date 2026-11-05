package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The two buttons right of an open name box on Prefabs, Groups and Mutators: a ✓ that commits the typed name as Enter
 * does, and a gold ↺ that closes the box leaving the name as Escape does.
 * Decision open-name-box-takes-a-done-checkmark.
 * Decision open-name-box-takes-a-gold-arrow-that-cancels.
 */
final class NameBoxButtons {
    private static final String DONE = "✓";
    private static final String DONE_HINT = "Done";
    private static final String NEVERMIND_HINT = "Nevermind";
    private static final int GAP = 2;

    private NameBoxButtons() {
    }

    /**
     * @param font the font
     * @return the width the two buttons take right of the box, their gaps included
     */
    static int width(Font font) {
        return GAP + ButtonPainter.width(font, DONE) + GAP + ButtonPainter.width(font, ButtonPainter.RESTORE);
    }

    /**
     * @param graphics   the frame's graphics
     * @param font       the font
     * @param boxRight   the box's right edge
     * @param lineTop    the top of the line the box stands on
     * @param lineHeight that line's height
     * @param targets    the frame's click areas
     * @param done       what Enter on the box runs
     * @param nevermind  what Escape on the box runs
     */
    static void draw(GuiGraphicsExtractor graphics, Font font, int boxRight, int lineTop, int lineHeight,
                     ClickTargets targets, Runnable done, Runnable nevermind) {
        ButtonPainter.Box doneBox = ButtonPainter.draw(graphics, font, DONE, boxRight + GAP, lineTop, lineHeight,
                targets, done, BuilderOverlays.hintKey(DONE_HINT));
        ButtonPainter.drawColored(graphics, font, ButtonPainter.RESTORE, Palette.GOLD, doneBox.right() + GAP,
                lineTop, lineHeight, targets, nevermind, BuilderOverlays.hintKey(NEVERMIND_HINT));
    }
}
