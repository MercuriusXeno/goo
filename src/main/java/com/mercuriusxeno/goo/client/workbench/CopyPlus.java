package com.mercuriusxeno.goo.client.workbench;

import com.mojang.blaze3d.platform.cursor.CursorType;

/**
 * The little pixel-art green plus the manager shows while a rail drag on a term or a parenthetical would drop a copy;
 * the hot spot is the plus's center.
 * Decision ctrl-during-a-rail-drag-shows-a-green-plus-and-drops-a-copy.
 */
final class CopyPlus {
    /** The image's path inside the mod's assets, beside the pointer hand's. */
    static final String TEXTURE_PATH = "textures/gui/cursor/copy_plus.png";
    /** The center's column in the image. */
    static final int HOT_X = 7;
    /** The center's row in the image. */
    static final int HOT_Y = 7;
    private static final PixelCursor PLUS = new PixelCursor(TEXTURE_PATH, HOT_X, HOT_Y, "goo_copy_plus");

    private CopyPlus() {
    }

    /**
     * @return the plus, built on the first call; the default cursor where the image or GLFW fails it
     */
    static CursorType cursor() {
        return PLUS.cursor();
    }
}
