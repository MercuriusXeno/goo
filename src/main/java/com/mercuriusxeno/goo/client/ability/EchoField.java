package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The walls around one spot a held sweep's pulses leave from: every face
 * open to air, found once as the farthest front reaches it and kept in
 * order of distance, so the pulses that leave the same spot after it light
 * the same walls without walking the cave again.
 * decision held-sweeps-share-one-fast-shell-visual
 */
final class EchoField {

    /** The most faces one spot keeps; past them the fronts light no more walls. */
    static final int MOST_FACES = 49_152;

    private final Vec3 center;
    private final List<EchoFace> faces = new ArrayList<>();
    /** The older fields whose walls this one may take rather than walk, until it retires. */
    private final List<EchoField> sources = new ArrayList<>();
    private double scannedTo;
    /** The radius within which every wall about the spot has been found; behind scannedTo once the field fills. */
    private double completeTo;
    private long shellPositions;

    /**
     * A field that walks the cave alone.
     *
     * @param center the spot the pulses leave from, at the caster's body
     */
    EchoField(Vec3 center) {
        this.center = center;
    }

    /**
     * A field that takes the walls the older fields about nearby spots
     * already found, rather than walk the cave again, wherever an older
     * field has found every wall: a caster moving while it holds sends each
     * pulse from a new spot (operator ruling 2026-11-19: reuse allowed, a
     * wall changed in the last seconds may light as an older field read it).
     * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
     *
     * @param center the spot the pulses leave from, at the caster's body
     * @param older  the fields standing as this one starts
     */
    EchoField(Vec3 center, Collection<EchoField> older) {
        this(center);
        sources.addAll(older);
    }

    /**
     * Lets go of the older fields, once no pulse leaves from this spot any
     * more, so fields never hold one another past their pulses.
     */
    void retire() {
        sources.clear();
    }

    /**
     * Finds the walls out to a radius not yet walked, appending them in
     * order of distance: taken from an older field as far as one has found
     * every wall, and walked past that.
     *
     * @param radius  the radius to walk out to
     * @param isAir   whether a position holds air
     * @param shapeOf a block's voxel shape boxes, block-local
     */
    void reachTo(double radius, Predicate<BlockPos> isAir, Function<BlockPos, List<AABB>> shapeOf) {
        reachTo(radius, isAir, shapeOf, AirSections.NONE);
    }

    /**
     * Finds the walls out to a radius not yet walked, as
     * {@link #reachTo(double, Predicate, Function)} finds them, reading no
     * block of a section that holds only air.
     *
     * @param radius  the radius to walk out to
     * @param isAir   whether a position holds air
     * @param shapeOf a block's voxel shape boxes, block-local
     * @param air     which sections hold only air
     */
    void reachTo(double radius, Predicate<BlockPos> isAir, Function<BlockPos, List<AABB>> shapeOf, AirSections air) {
        if (radius <= scannedTo) {
            return;
        }
        if (faces.size() >= MOST_FACES) {
            // a full field keeps no more walls, so the shell the front crossed is never built
            scannedTo = radius;
            return;
        }
        takeFromSources(radius);
        if (radius > scannedTo && faces.size() < MOST_FACES) {
            walk(radius, isAir, shapeOf, air);
        }
        scannedTo = radius;
    }

    /**
     * Takes, from the older field that found every wall farthest past this
     * spot, the walls between where this field stands and that reach.
     *
     * @param radius the radius to reach out to at most
     */
    private void takeFromSources(double radius) {
        EchoField source = null;
        double reach = scannedTo;
        for (EchoField older : sources) {
            double covers = older.completeTo - older.center.distanceTo(center);
            if (covers > reach) {
                source = older;
                reach = covers;
            }
        }
        if (source != null) {
            List<EchoFace> band = source.facesBetween(center, scannedTo, Math.min(radius, reach));
            append(band, Math.min(radius, reach), band.size() <= MOST_FACES - faces.size());
        }
    }

    /**
     * The walls this field found whose centers lie past one radius and
     * within another of another spot, measured from that spot.
     *
     * @param spot  the other spot
     * @param inner the inner radius about it, exclusive
     * @param outer the outer radius about it, inclusive
     * @return the walls, measured from the spot
     */
    private List<EchoFace> facesBetween(Vec3 spot, double inner, double outer) {
        double apart = center.distanceTo(spot);
        List<EchoFace> band = new ArrayList<>();
        for (int index = firstPast(inner - apart); index < faces.size(); index++) {
            EchoFace face = faces.get(index);
            if (face.distance() > outer + apart) {
                break;
            }
            double distance = face.middle().distanceTo(spot);
            if (distance > inner && distance <= outer) {
                band.add(new EchoFace(face.pos(), face.sides(), face.boxes(), distance, face.middle()));
            }
        }
        return band;
    }

    /**
     * @param radius a distance from the spot
     * @return the index of the first wall, nearest first, whose distance passes the radius
     */
    private int firstPast(double radius) {
        int low = 0;
        int high = faces.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (faces.get(middle).distance() <= radius) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    private void walk(double radius, Predicate<BlockPos> isAir, Function<BlockPos, List<AABB>> shapeOf,
                      AirSections air) {
        List<EchoFace> band = new ArrayList<>();
        List<BlockPos> shell = ScryReveal.shell(center, scannedTo, radius);
        shellPositions += shell.size();
        AirMemo airAlone = air == AirSections.NONE ? null : new AirMemo(center, radius, air);
        boolean whole = true;
        for (BlockPos pos : shell) {
            if (faces.size() + band.size() >= MOST_FACES) {
                whole = false;
                break;
            }
            if (airAlone != null && airAlone.holdsOnlyAir(pos)) {
                continue;
            }
            List<Direction> sides = ScryReveal.exposedFaces(isAir, pos);
            if (!sides.isEmpty()) {
                Vec3 middle = Vec3.atCenterOf(pos);
                band.add(new EchoFace(pos, sides, shapeOf.apply(pos), middle.distanceTo(center), middle));
            }
        }
        append(band, radius, whole);
    }

    /**
     * One walk's answers to which sections hold only air, each section about
     * the spot asked once and kept in a grid, since the walk steps between
     * the two ends of each column and so between sections at nearly every
     * block.
     * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
     */
    private static final class AirMemo {

        private static final int SECTION_SHIFT = 4;
        private static final byte UNASKED = 0;
        private static final byte SOLID_TOO = 1;
        private static final byte AIR_ALONE = 2;
        private static final int SPARE_SECTIONS = 2;

        private final AirSections air;
        private final int lowX;
        private final int lowY;
        private final int lowZ;
        private final int span;
        private final byte[] answers;

        AirMemo(Vec3 center, double radius, AirSections air) {
            this.air = air;
            int reach = (int) Math.ceil(radius) + 1;
            lowX = ((int) Math.floor(center.x) - reach) >> SECTION_SHIFT;
            lowY = ((int) Math.floor(center.y) - reach) >> SECTION_SHIFT;
            lowZ = ((int) Math.floor(center.z) - reach) >> SECTION_SHIFT;
            // the sphere's width in sections, and one more at each end where its edge falls mid-section
            span = ((reach + reach) >> SECTION_SHIFT) + SPARE_SECTIONS;
            answers = new byte[span * span * span];
        }

        boolean holdsOnlyAir(BlockPos pos) {
            int sectionX = pos.getX() >> SECTION_SHIFT;
            int sectionY = pos.getY() >> SECTION_SHIFT;
            int sectionZ = pos.getZ() >> SECTION_SHIFT;
            if (!inGrid(sectionX - lowX) || !inGrid(sectionY - lowY) || !inGrid(sectionZ - lowZ)) {
                return air.allAir(sectionX, sectionY, sectionZ);
            }
            int index = ((sectionX - lowX) * span + sectionY - lowY) * span + sectionZ - lowZ;
            if (answers[index] == UNASKED) {
                answers[index] = air.allAir(sectionX, sectionY, sectionZ) ? AIR_ALONE : SOLID_TOO;
            }
            return answers[index] == AIR_ALONE;
        }

        private boolean inGrid(int offset) {
            return offset >= 0 && offset < span;
        }
    }

    /**
     * Appends a band of walls in order of distance, as many as the field keeps.
     *
     * @param band   the band's walls
     * @param radius the radius the band reaches
     * @param whole  whether the band holds every wall out to the radius and the field keeps them all
     */
    private void append(List<EchoFace> band, double radius, boolean whole) {
        band.sort(Comparator.comparingDouble(EchoFace::distance));
        faces.addAll(band.subList(0, Math.min(band.size(), MOST_FACES - faces.size())));
        if (whole) {
            completeTo = radius;
        }
        scannedTo = radius;
    }

    /**
     * The spot the pulses leave from.
     *
     * @return the spot, at the caster's body
     */
    Vec3 center() {
        return center;
    }

    /**
     * The positions of every shell the walk built so far, the walk's work
     * beside the walls it found.
     * decision diagnose-and-correct-the-lag-on-four-sense-abilities
     *
     * @return how many
     */
    long shellPositions() {
        return shellPositions;
    }

    /**
     * The walls found so far.
     *
     * @return how many
     */
    int size() {
        return faces.size();
    }

    /**
     * One wall, nearest first.
     *
     * @param index its place in order of distance
     * @return the wall
     */
    EchoFace face(int index) {
        return faces.get(index);
    }

    /**
     * A block whose faces open to air.
     *
     * @param pos      the block
     * @param sides    its sides open to air
     * @param boxes    its voxel shape's boxes, block-local, so slabs and stairs light as they stand
     * @param distance its center's distance from the spot
     * @param middle   its center, which a frame's walls sort by
     */
    record EchoFace(BlockPos pos, List<Direction> sides, List<AABB> boxes, double distance, Vec3 middle) {
    }
}
