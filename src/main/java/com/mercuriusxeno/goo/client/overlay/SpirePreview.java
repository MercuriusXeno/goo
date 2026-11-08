package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.SpireFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What Spire's preview draws: the ripple echo's rings across the footprint's
 * ground cells, the tops the stone grows from, each ring the footprint's own
 * outline scaled from its center, over a steady outline of its edge; and once the footprint is fixed the ghost
 * stone filling every cell the rise would fill above them, only its outer
 * faces, so no seam shows between its blocks.
 * decision spire-previews-as-a-surface-ripple-and-ghost-stone
 */
final class SpirePreview {

    /** How far above the ground the rings float, so they never fight the ground's own face. */
    static final double RING_LIFT = 0.02;
    private static final double HALF = 0.5;

    private SpirePreview() {
    }

    /**
     * One ring of the ripple echo at a phase: the footprint's outline scaled
     * from its center by the ring's phase (RippleRings), lying just above the
     * ground, closed on its first corner.
     *
     * @param footprint the live footprint
     * @param phase     the ring's phase, 0 at the center to 1 at the footprint's edge
     * @return the ring's corners, north-west, south-west, south-east, north-east, then north-west again
     */
    static Vec3[] ringCorners(SpireFootprint footprint, double phase) {
        BlockPos min = footprint.min();
        BlockPos max = footprint.max();
        double centerX = (min.getX() + max.getX() + 1) * HALF;
        double centerZ = (min.getZ() + max.getZ() + 1) * HALF;
        double halfX = RippleRings.ringRadius(phase, (max.getX() + 1 - min.getX()) * HALF);
        double halfZ = RippleRings.ringRadius(phase, (max.getZ() + 1 - min.getZ()) * HALF);
        double y = min.getY() + 1 + RING_LIFT;
        Vec3 northWest = new Vec3(centerX - halfX, y, centerZ - halfZ);
        return new Vec3[] {northWest, new Vec3(centerX - halfX, y, centerZ + halfZ),
            new Vec3(centerX + halfX, y, centerZ + halfZ), new Vec3(centerX + halfX, y, centerZ - halfZ), northWest};
    }

    /**
     * The steady outline under the rings: the footprint's own edge, the line
     * every ring grows out to, so the footprint reads clearly between rings.
     *
     * @param footprint the live footprint
     * @return the edge's corners, closed on its first
     */
    static Vec3[] edgeOutline(SpireFootprint footprint) {
        return ringCorners(footprint, 1);
    }

    /**
     * One face of a block in a cell, by the side it faces.
     *
     * @param cell the cell the block stands in
     * @param side the side the face faces
     */
    record Face(BlockPos cell, Direction side) {
    }

    /**
     * The ghost's outer faces of a cell as seen from inside the ghost: each
     * drawn as the face the outside neighbour would show, on the same plane
     * but facing in, so a holder standing inside the footprint sees the ghost
     * around them; from outside, culling hides these as it hides the outer
     * faces from inside.
     *
     * @param cell  the ghost cell
     * @param ghost every ghost cell
     * @return the inward faces
     */
    static List<Face> inwardFaces(BlockPos cell, Set<BlockPos> ghost) {
        return outerFaces(cell, ghost).stream().map(side -> new Face(cell.relative(side), side.getOpposite())).toList();
    }

    /**
     * The faces of a ghost cell no other ghost cell covers, the only ones the ghost draws.
     *
     * @param cell  the ghost cell
     * @param ghost every ghost cell
     * @return its outer faces
     */
    static List<Direction> outerFaces(BlockPos cell, Set<BlockPos> ghost) {
        List<Direction> faces = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (!ghost.contains(cell.relative(side))) {
                faces.add(side);
            }
        }
        return faces;
    }

    /**
     * The cells the ripple spans: the footprint's ground cells, whose tops its rings cross.
     *
     * @param footprint the live footprint
     * @return the ground cells
     */
    static List<BlockPos> rippleCells(SpireFootprint footprint) {
        return footprint.groundCells();
    }

    /**
     * The cells the ghost stone fills: above each ground cell, from the cell
     * on top of it up through the rise.
     *
     * @param footprint the footprint, at its rise
     * @return the cells, column by column
     */
    static List<BlockPos> ghostCells(SpireFootprint footprint) {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos ground : footprint.groundCells()) {
            for (int height = 1; height <= footprint.rise(); height++) {
                cells.add(ground.above(height));
            }
        }
        return cells;
    }
}
