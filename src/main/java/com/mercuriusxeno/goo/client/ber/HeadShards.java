package com.mercuriusxeno.goo.client.ber;

import com.mojang.math.Axis;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One melting item's shards as the crucible draws them, worked out once per item: the model
 * broken into shards, how the item lies (a flat item face up, a block upright as it stands),
 * each shard's centroid and footprint on the head's texel lattice, and each shard's rest shift
 * (decisions tiles-of-the-items-image, tiles-break-off-as-dissolve-advances).
 *
 * @param model     the model broken into shards
 * @param lying     turns the model's space into the lying item's, about the model's center
 * @param scale     blocks per unit of the model's space
 * @param centroids each shard's centroid in the model's space
 * @param pieces    each shard as the layout sees it
 * @param frame     the head's lattice and bottom
 * @param shifts    each shard's rest shift in lattice cells
 */
record HeadShards(ShardedModel model, Matrix4fc lying, float scale, List<float[]> centroids,
                  List<CrucibleItemLayout.ShardPiece> pieces, CrucibleItemLayout.HeadFrame frame, int[][] shifts) {

    /** The smallest span a model is scaled by, so an empty model never divides by zero. */
    private static final float MIN_SPAN = 1e-3f;
    private static final int CORNERS = 8;
    /** A cell's half diagonal per unit of its width, from a cell's center to its corner. */
    private static final float HALF_DIAGONAL = (float) (Math.sqrt(2.0) / 2.0);
    private static final int HIGH_X = 1;
    private static final int HIGH_Y = 2;
    private static final int HIGH_Z = 4;

    /**
     * How the item lies: the turn from the model's space into the lying item's, about the
     * model's center, and the blocks per unit of the model's space.
     *
     * @param turn   the lying turn
     * @param center the model's center
     * @param scale  blocks per unit of the model's space
     */
    private record Lying(Matrix4fc turn, Vector3f center, float scale) {

        static Lying of(ShardedModel model) {
            Matrix4f turn = model.flat()
                    ? new Matrix4f().rotation(Axis.XP.rotationDegrees(CrucibleHeadHandoff.FLAT_TILT_DEGREES))
                    : new Matrix4f();
            float headSize = model.flat() ? CrucibleItemLayout.HEAD_SIZE : CrucibleItemLayout.BLOCK_HEAD_SIZE;
            AABB box = model.box();
            return new Lying(turn, new Vector3f((float) box.getCenter().x, (float) box.getCenter().y,
                    (float) box.getCenter().z), headSize / Math.max(model.span(), MIN_SPAN));
        }

        Vector3f lie(float[] point) {
            return HeadShards.lie(turn, center, scale, point);
        }

        /**
         * @param box the model's bounding box
         * @return the bounds the box reaches lying, relative to the head's center
         */
        AxisBounds reach(AABB box) {
            AxisBounds reach = new AxisBounds();
            for (int corner = 0; corner < CORNERS; corner++) {
                Vector3f at = lie(new float[] {
                    (float) ((corner & HIGH_X) == 0 ? box.minX : box.maxX),
                    (float) ((corner & HIGH_Y) == 0 ? box.minY : box.maxY),
                    (float) ((corner & HIGH_Z) == 0 ? box.minZ : box.maxZ)});
                reach.include(at.x(), at.y(), at.z());
            }
            return reach;
        }
    }

    /**
     * The head's texel lattice seen from above: its frame and how many cells it spans.
     *
     * @param frame   the lattice's origin, cell and the head's bottom
     * @param columns the cells along X
     * @param rows    the cells along Z
     */
    private record Lattice(CrucibleItemLayout.HeadFrame frame, int columns, int rows) {

        static Lattice of(AxisBounds reach, float cell) {
            return new Lattice(new CrucibleItemLayout.HeadFrame(reach.low(QuadRectClipper.X),
                    reach.low(QuadRectClipper.Z), cell, reach.low(QuadRectClipper.Y)),
                    Math.max(1, Math.round(reach.extent(QuadRectClipper.X) / cell)),
                    Math.max(1, Math.round(reach.extent(QuadRectClipper.Z) / cell)));
        }

        long cellOf(Vector3f at) {
            int column = Math.clamp((int) Math.floor((at.x() - frame.originX()) / frame.cell()), 0, columns - 1);
            int row = Math.clamp((int) Math.floor((at.z() - frame.originZ()) / frame.cell()), 0, rows - 1);
            return ((long) column << Integer.SIZE) | Integer.toUnsignedLong(row);
        }
    }

    /**
     * Works out an item's shards for the crucible.
     *
     * @param model the model broken into shards
     * @param seed  the item's seed, which the rest shifts are drawn from
     * @return the head's shards
     */
    static HeadShards of(ShardedModel model, long seed) {
        Lying lying = Lying.of(model);
        Lattice lattice = Lattice.of(lying.reach(model.box()), model.texel() * lying.scale());
        List<float[]> centroids = new ArrayList<>(model.count());
        List<CrucibleItemLayout.ShardPiece> pieces = new ArrayList<>(model.count());
        for (int shard = 0; shard < model.count(); shard++) {
            float[] centroid = centroidOf(model.cellsOf(shard));
            centroids.add(centroid);
            pieces.add(pieceOf(model.cellsOf(shard), lying.lie(centroid), lying, lattice));
        }
        return new HeadShards(model, lying.turn(), lying.scale(), centroids, pieces, lattice.frame(),
                CrucibleItemLayout.restShifts(pieces, lattice.frame(), seed));
    }

    private static CrucibleItemLayout.ShardPiece pieceOf(List<float[]> cells, Vector3f centroid, Lying lying,
                                                         Lattice lattice) {
        float floor = Float.MAX_VALUE;
        float reach = 0f;
        Set<Long> covered = new LinkedHashSet<>();
        for (float[] cell : cells) {
            Vector3f at = lying.lie(cell);
            floor = Math.min(floor, at.y());
            reach = Math.max(reach, (float) Math.hypot(at.x() - centroid.x(), at.z() - centroid.z()));
            covered.add(lattice.cellOf(at));
        }
        int[][] footprint = new int[covered.size()][];
        int i = 0;
        for (long key : covered) {
            footprint[i++] = new int[] {(int) (key >> Integer.SIZE), (int) key};
        }
        return new CrucibleItemLayout.ShardPiece(centroid.x(), centroid.y(), centroid.z(), floor, footprint,
                reach + lattice.frame().cell() * HALF_DIAGONAL);
    }

    private static float[] centroidOf(List<float[]> cells) {
        float[] sum = new float[AxisBounds.AXES];
        for (float[] cell : cells) {
            for (int axis = 0; axis < sum.length; axis++) {
                sum[axis] += cell[axis];
            }
        }
        for (int axis = 0; axis < sum.length; axis++) {
            sum[axis] /= Math.max(cells.size(), 1);
        }
        return sum;
    }

    /**
     * Carries a point of the model's space into the lying item's, in blocks about its center.
     *
     * @param lying  the lying turn
     * @param center the model's center
     * @param scale  blocks per unit of the model's space
     * @param point  the point, X, Y and Z
     * @return the point lying, relative to the head's center
     */
    static Vector3f lie(Matrix4fc lying, Vector3f center, float scale, float[] point) {
        return lying.transformPosition(new Vector3f(point[QuadRectClipper.X], point[QuadRectClipper.Y],
                point[QuadRectClipper.Z]).sub(center)).mul(scale);
    }
}
