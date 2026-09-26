package com.mercuriusxeno.goo.block.canister;

import net.minecraft.core.Direction;

/**
 * What the client knows about the viewer that a holder's HUD anchor turns on,
 * read client-side so the holder stays free of client classes.
 *
 * @param facing     the horizontal direction the player faces
 * @param lookFace   the horizontal face most perpendicular to the player's look vector
 * @param blockAbove true when a full block sits above the aimed holder
 */
public record HudViewer(Direction facing, Direction lookFace, boolean blockAbove) {
}
