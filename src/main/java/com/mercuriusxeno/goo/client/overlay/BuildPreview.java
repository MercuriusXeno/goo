package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.BuildColumn;
import com.mercuriusxeno.goo.ability.BuildFootprint;
import com.mercuriusxeno.goo.ability.BuildGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * What Build's preview draws: the ripple echo's rings across the footprint's
 * ground cells, the tops the stone grows from, each ring the footprint's own
 * outline scaled from its center, over a steady outline of its edge; and once the footprint is fixed the ghost
 * stone filling every cell the rise would fill above each column the plan grows, only its outer
 * faces, so no seam shows between its blocks.
 * decision spire-previews-as-a-surface-ripple-and-ghost-stone
 */
final class BuildPreview {

    /** How far above the ground the rings float, so they never fight the ground's own face. */
    static final double RING_LIFT = 0.02;
    private static final double HALF = 0.5;

    private BuildPreview() {
    }

    /**
     * One ring of the ripple echo at a phase: the footprint's outline scaled
     * from its center by the ring's phase (RippleRings), lying just off the
     * face the footprint lies on, closed on its first corner.
     * decision build-goes-sideways
     *
     * @param footprint the live footprint
     * @param phase     the ring's phase, 0 at the center to 1 at the footprint's edge
     * @return the ring's corners, closed on the first; on the ground north-west, south-west, south-east, north-east
     */
    static Vec3[] ringCorners(BuildFootprint footprint, double phase) {
        Direction face = footprint.face();
        Direction.Axis normal = face.getAxis();
        Direction.Axis[] across = Arrays.stream(Direction.Axis.VALUES).filter(axis -> axis != normal)
                .toArray(Direction.Axis[]::new);
        BlockPos min = footprint.min();
        BlockPos max = footprint.max();
        double[] center = new double[across.length];
        double[] half = new double[across.length];
        for (int i = 0; i < across.length; i++) {
            center[i] = (min.get(across[i]) + max.get(across[i]) + 1) * HALF;
            half[i] = RippleRings.ringRadius(phase, (max.get(across[i]) + 1 - min.get(across[i])) * HALF);
        }
        boolean outward = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double plane = outward ? max.get(normal) + 1 + RING_LIFT : min.get(normal) - RING_LIFT;
        Vec3 first = pointOn(normal, plane, across, center[0] - half[0], center[1] - half[1]);
        return new Vec3[] {first, pointOn(normal, plane, across, center[0] - half[0], center[1] + half[1]),
            pointOn(normal, plane, across, center[0] + half[0], center[1] + half[1]),
            pointOn(normal, plane, across, center[0] + half[0], center[1] - half[1]), first};
    }

    private static Vec3 pointOn(Direction.Axis normal, double plane, Direction.Axis[] across, double u, double v) {
        double[] point = new double[Direction.Axis.VALUES.length];
        point[normal.ordinal()] = plane;
        point[across[0].ordinal()] = u;
        point[across[1].ordinal()] = v;
        return new Vec3(point[Direction.Axis.X.ordinal()], point[Direction.Axis.Y.ordinal()],
                point[Direction.Axis.Z.ordinal()]);
    }

    /**
     * The steady outline under the rings: the footprint's own edge, the line
     * every ring grows out to, so the footprint reads clearly between rings.
     *
     * @param footprint the live footprint
     * @return the edge's corners, closed on its first
     */
    static Vec3[] edgeOutline(BuildFootprint footprint) {
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
    static List<BlockPos> rippleCells(BuildFootprint footprint) {
        return footprint.groundCells();
    }

    /**
     * The cells the ghost stone fills: out from each planned column's ground
     * cell along its face's normal, from the cell in front of it out through
     * the rise, so a column the plan fizzles or closes shows none.
     * decision column-meeting-a-block-entity-fizzles-at-no-cost
     *
     * @param growth the planned growth
     * @return the cells, column by column
     */
    static List<BlockPos> ghostCells(BuildGrowth growth) {
        Direction face = growth.footprint().face();
        List<BlockPos> cells = new ArrayList<>();
        for (BuildColumn column : growth.columns()) {
            for (int height = 1; height <= column.rise(); height++) {
                cells.add(column.ground().relative(face, height));
            }
        }
        return cells;
    }
}
