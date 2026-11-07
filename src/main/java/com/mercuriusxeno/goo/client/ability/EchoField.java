package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
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
    private double scannedTo;

    /**
     * @param center the spot the pulses leave from, at the caster's body
     */
    EchoField(Vec3 center) {
        this.center = center;
    }

    /**
     * Finds the walls out to a radius not yet walked, appending them in
     * order of distance.
     *
     * @param radius  the radius to walk out to
     * @param isAir   whether a position holds air
     * @param shapeOf a block's voxel shape boxes, block-local
     */
    void reachTo(double radius, Predicate<BlockPos> isAir, Function<BlockPos, List<AABB>> shapeOf) {
        if (radius <= scannedTo) {
            return;
        }
        List<EchoFace> band = new ArrayList<>();
        for (BlockPos pos : ScryReveal.shell(center, scannedTo, radius)) {
            if (faces.size() + band.size() >= MOST_FACES) {
                break;
            }
            List<Direction> sides = ScryReveal.exposedFaces(isAir, pos);
            if (!sides.isEmpty()) {
                band.add(new EchoFace(pos, sides, shapeOf.apply(pos), Vec3.atCenterOf(pos).distanceTo(center)));
            }
        }
        band.sort(Comparator.comparingDouble(EchoFace::distance));
        faces.addAll(band);
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
     */
    record EchoFace(BlockPos pos, List<Direction> sides, List<AABB> boxes, double distance) {
    }
}
