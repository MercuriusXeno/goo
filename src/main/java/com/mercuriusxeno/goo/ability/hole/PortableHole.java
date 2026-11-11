package com.mercuriusxeno.goo.ability.hole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/**
 * A player's portable hole standing open on a block's face: on a top face it
 * swallows the items that fall into its mouth onto its owner's shelf, and on a
 * bottom face it poured the shelf out as it opened.
 * decision hole-is-an-in-world-portable-hole-for-items
 *
 * @param owner     the player whose hole it is
 * @param dimension the level it stands in
 * @param block     the block whose face it opens on
 * @param face      the face it opens on
 * @param closesAt  the game time it closes
 */
public record PortableHole(UUID owner, ResourceKey<Level> dimension, BlockPos block, Direction face, long closesAt) {

    /** How far the mouth reaches out from its face, so an item resting on the face stands in it. */
    private static final double MOUTH_DEPTH = 0.25;

    /**
     * @return whether the hole swallows what falls into it, true on a top face
     */
    public boolean swallows() {
        return face == Direction.UP;
    }

    /**
     * The space just off the face an item must meet to fall in.
     *
     * @return the mouth's box
     */
    public AABB mouth() {
        // the cell beyond the face, cut back from its far side to the slab nearest the face
        Vec3 cutBack = Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(1 - MOUTH_DEPTH);
        return new AABB(block.relative(face)).contract(cutBack.x(), cutBack.y(), cutBack.z());
    }
}
