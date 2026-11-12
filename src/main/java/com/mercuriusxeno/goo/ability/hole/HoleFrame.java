package com.mercuriusxeno.goo.ability.hole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * A hole's mouth in its own frame, the one its stored items swim in: the
 * opening's middle, the unit axes across its face, off it and along it, and
 * the half spans the pit's rim leaves the items. The client draws the soup in
 * this frame and the server pours each kind from its place in it, so both
 * read one geometry.
 * decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole
 * decision hole-pours-each-kind-from-where-it-swims-once-open
 *
 * @param centre     the opening's middle
 * @param across     the axis across the face
 * @param normal     the face's outward normal
 * @param along      the axis along the face
 * @param halfAcross the pit rim's half span across
 * @param halfAlong  the pit rim's half span along
 */
public record HoleFrame(Vec3 centre, Vec3 across, Vec3 normal, Vec3 along, double halfAcross, double halfAlong) {

    /** How far the pit's walls stand inside the footprint's outer faces. */
    public static final double PIT_INSET = 0.05;
    /** A box's face's distance from its center, as a share of the box's size. */
    private static final double FACE_OFFSET = 0.5;

    /**
     * The frame of a mouth across a footprint's faces.
     *
     * @param footprint the blocks whose faces the mouth covers
     * @param face      the face it opens on
     * @return the frame
     */
    public static HoleFrame of(List<BlockPos> footprint, Direction face) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        Vec3 across = acrossOf(face);
        Vec3 along = normal.cross(across);
        // the items keep within the pit's walls, which stand inside the footprint's outer faces
        AABB pitRim = pitOf(footprint, face).rim();
        return new HoleFrame(openingOn(footprint, face).getCenter(), across, normal, along, halfSpan(pitRim, across),
                halfSpan(pitRim, along));
    }

    /**
     * @param offset a place in the mouth's frame
     * @return the place in the world
     */
    public Vec3 toWorld(Vec3 offset) {
        return centre.add(across.scale(offset.x())).add(normal.scale(offset.y())).add(along.scale(offset.z()));
    }

    /**
     * @param world a place in the world
     * @return the place in the mouth's frame
     */
    public Vec3 toFrame(Vec3 world) {
        Vec3 off = world.subtract(centre);
        return new Vec3(off.dot(across), off.dot(normal), off.dot(along));
    }

    /**
     * The axis across a face, the frame's first.
     *
     * @param face the face
     * @return the unit axis
     */
    public static Vec3 acrossOf(Direction face) {
        return face.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
    }

    /**
     * The flat rectangle a mouth opens across: the faces of its footprint's
     * blocks on the side the mouth stands on, the oval of its opening
     * inscribed in it.
     * decision hole-mouth-opens-three-by-three-and-fits-its-room
     *
     * @param footprint the blocks whose faces the mouth covers
     * @param face      the face it opens on
     * @return the rectangle, flat along the face's normal
     */
    public static AABB openingOn(List<BlockPos> footprint, Direction face) {
        AABB cells = footprint.stream().map(AABB::new).reduce(AABB::minmax).orElseThrow();
        Direction.Axis normal = face.getAxis();
        double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? cells.max(normal)
                : cells.min(normal);
        return new AABB(cells.getMinPosition().with(normal, plane), cells.getMaxPosition().with(normal, plane));
    }

    /**
     * How far an opening reaches from its middle along an axis across its face.
     *
     * @param opening the opening's flat rectangle
     * @param axis    a unit axis along the face
     * @return the half span, in blocks
     */
    public static double halfSpan(AABB opening, Vec3 axis) {
        return (Math.abs(axis.x()) * opening.getXsize() + Math.abs(axis.y()) * opening.getYsize()
                + Math.abs(axis.z()) * opening.getZsize()) * FACE_OFFSET;
    }

    /**
     * The space behind a mouth: its rim, a flat rectangle on the face plane,
     * and its floor, the same rectangle sunk the pit's depth into the blocks.
     *
     * @param rim   the pit's rim on the face plane
     * @param floor the pit's floor
     */
    public record Pit(AABB rim, AABB floor) {
    }

    /**
     * The pit behind a mouth's footprint.
     *
     * @param footprint the blocks whose faces the mouth covers
     * @param face      the face it opens on
     * @return the pit
     */
    public static Pit pitOf(List<BlockPos> footprint, Direction face) {
        Vec3 inward = Vec3.atLowerCornerOf(face.getOpposite().getUnitVec3i());
        // the walls stand inside the blocks' outer faces, never in their plane, so the two never fight for depth
        Vec3 inset = new Vec3(1 - Math.abs(inward.x()), 1 - Math.abs(inward.y()), 1 - Math.abs(inward.z()))
                .scale(PIT_INSET);
        AABB rim = openingOn(footprint, face).deflate(inset.x(), inset.y(), inset.z());
        return new Pit(rim, rim.move(inward.scale(HoleDrift.PIT_DEPTH)));
    }
}
