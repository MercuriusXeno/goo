package com.mercuriusxeno.goo.ability.hole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.UUID;

/**
 * A player's portable hole standing open across a footprint of blocks' faces:
 * on top faces it swallows the items that fall into its mouth onto its
 * owner's shelf, and on bottom faces it poured the shelf out as it opened.
 * decision hole-is-an-in-world-portable-hole-for-items
 * decision hole-mouth-opens-three-by-three-and-fits-its-room
 *
 * @param owner     the player whose hole it is
 * @param dimension the level it stands in
 * @param block     the block the cast struck
 * @param face      the face it opens on
 * @param footprint the blocks whose faces its mouth covers, the struck block among them
 * @param openedAt  the game time it opened
 * @param closesAt  the game time it closes
 * @param refresh   the ticks left each swallow raises its time left to when less remains
 */
public record PortableHole(UUID owner, ResourceKey<Level> dimension, BlockPos block, Direction face,
                           List<BlockPos> footprint, long openedAt, long closesAt, int refresh) {

    /** How far the mouth reaches out from its face, so an item resting on the face stands in it. */
    private static final double MOUTH_DEPTH = 0.25;

    /**
     * @return whether the hole swallows what falls into it, true on a top face
     */
    public boolean swallows() {
        return face == Direction.UP;
    }

    /**
     * The hole after it swallows: its time left raised to the refresh when
     * less remains, and left be when more remains.
     * decision hole-holds-twelve-seconds-and-each-throw-in-floors-six
     *
     * @param now the game time it swallowed
     * @return the hole as it stands after the swallow
     */
    public PortableHole fedAt(long now) {
        long floored = Math.max(closesAt, now + refresh);
        return floored == closesAt ? this
                : new PortableHole(owner, dimension, block, face, footprint, openedAt, floored, refresh);
    }

    /**
     * The blocks the footprint covers, as one box one block deep.
     *
     * @return the box
     */
    public AABB blocks() {
        return footprint.stream().map(AABB::new).reduce(AABB::minmax).orElseThrow();
    }

    /**
     * The space just off the footprint's faces an item must meet to fall in.
     *
     * @return the mouth's box
     */
    public AABB mouth() {
        // the cells beyond the faces, cut back from their far side to the slab nearest the faces
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        Vec3 cutBack = normal.scale(1 - MOUTH_DEPTH);
        return blocks().move(normal).contract(cutBack.x(), cutBack.y(), cutBack.z());
    }
}
