package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;

/**
 * Which voidstone draws its stars: a block lying wholly inside the room's
 * boundary does, and the block the boundary cuts, the one a build refuses,
 * draws its plain dark faces, so the edge of what may be built on shows. The
 * test reads the boundary the client holds now, so a block gains its stars
 * on the edge update that clears its far face, with no section rebuilt. A
 * block beyond the boundary is seen only through the edge views, which draw
 * the room's own inside, and a player standing in no known room sees every
 * voidstone starred.
 * decision boundary-cut-voidstone-draws-without-its-stars
 */
public final class VoidstoneStars {

    private VoidstoneStars() {
    }

    /**
     * Whether a voidstone draws its stars.
     *
     * @param x the block's x
     * @param z the block's z
     * @return true where its column lies wholly inside the room's boundary, or no room is known
     */
    public static boolean starred(int x, int z) {
        return ClientRoomEdges.bounds().map(bounds -> bounds.holdsWholeBlock(x, z)).orElse(true);
    }
}
