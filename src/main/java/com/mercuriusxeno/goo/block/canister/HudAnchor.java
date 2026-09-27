package com.mercuriusxeno.goo.block.canister;

import net.minecraft.core.Direction;

/**
 * Where a holder anchors the canister HUD panel for the slot under the cursor,
 * in block-local coordinates (decision hosts-answer-bounds-through-interfaces).
 *
 * @param slot the slot the panel reads, or {@code NO_SLOT} for a holder's frame
 * @param x    the block-local X of the anchor
 * @param z    the block-local Z of the anchor
 * @param lift the block-local Y of the anchor
 * @param face the face the panel sits on
 */
public record HudAnchor(int slot, double x, double z, double lift, Direction face) {
}
