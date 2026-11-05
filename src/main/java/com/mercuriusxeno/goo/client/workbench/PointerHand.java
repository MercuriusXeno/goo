package com.mercuriusxeno.goo.client.workbench;

import com.mojang.blaze3d.platform.cursor.CursorType;

/**
 * The little pixel-art pointer hand the manager shows while shift is held over a link; the hot spot is the fingertip.
 * Decision shift-over-a-link-shows-a-pixel-pointer-hand.
 */
final class PointerHand {
    /** The image's path inside the mod's assets. */
    static final String TEXTURE_PATH = "textures/gui/cursor/pointer_hand.png";
    /** The fingertip's column in the image. */
    static final int HOT_X = 5;
    /** The fingertip's row in the image. */
    static final int HOT_Y = 0;
    private static final PixelCursor HAND = new PixelCursor(TEXTURE_PATH, HOT_X, HOT_Y, "goo_pointer_hand");

    private PointerHand() {
    }

    /**
     * @return the hand, built on the first call; the default cursor where the image or GLFW fails it
     */
    static CursorType cursor() {
        return HAND.cursor();
    }
}
