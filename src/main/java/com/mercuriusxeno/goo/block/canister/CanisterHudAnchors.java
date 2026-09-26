package com.mercuriusxeno.goo.block.canister;

import net.minecraft.core.Direction;

/**
 * Where the canister block anchors the HUD panel for a grid slot: under the
 * block viewed from below, over the body top when the block above is open,
 * and on a side face otherwise (decision hosts-answer-bounds-through-interfaces).
 */
final class CanisterHudAnchors {
    /**
     * Block-local coordinate to pixel conversion factor.
     */
    private static final double BLOCK_PIXELS = 16.0;
    /**
     * Canister body height in blocks (12/16).
     */
    private static final double BODY_HEIGHT = 12.0 / 16.0;
    /**
     * Mid-body Y for side-face anchoring (6/16).
     */
    private static final double MID_BODY = 6.0 / 16.0;
    /**
     * Y for the panel under the block bottom.
     */
    private static final double BLOCK_BOTTOM = 0.0;
    /**
     * Offset pushing a side panel out to the face surface (half a block).
     */
    private static final double FACE_OFFSET = 0.5;

    private CanisterHudAnchors() {
    }

    /**
     * @param slot   the aimed grid slot
     * @param face   the face the hit landed on
     * @param viewer what the client knows about the viewer
     * @return the slot's anchor
     */
    static HudAnchor anchor(int slot, Direction face, HudViewer viewer) {
        double cx = CanisterSlotLayout.SLOT_CENTERS[slot][0] / BLOCK_PIXELS;
        double cz = CanisterSlotLayout.SLOT_CENTERS[slot][1] / BLOCK_PIXELS;
        if (face == Direction.DOWN) {
            return new HudAnchor(slot, cx, cz, BLOCK_BOTTOM, Direction.DOWN);
        }
        if (!viewer.blockAbove()) {
            return new HudAnchor(slot, cx, cz, BODY_HEIGHT, Direction.UP);
        }
        Direction side = face == Direction.UP ? viewer.facing() : face;
        return new HudAnchor(slot, cx + side.getStepX() * FACE_OFFSET, cz + side.getStepZ() * FACE_OFFSET,
                MID_BODY, side);
    }
}
