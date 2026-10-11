package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.PrismColumn;
import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The plain prism's crystal: one six-sided milky quartz column with a pointed tip,
 * half a block wide, standing out of the center of the face the prism landed on,
 * drawn through {@link CrystalClusterSubmitter} as the crystallizer draws its prisms.
 * The landing blob turns into it: the blob's cube, sitting on the face it struck,
 * bends its four sides out into the column's six and draws its top out into the point.
 * A Relay or Reflector column then folds its six sides into four as its combo takes.
 * decision prism-is-one-pointed-quartz-column
 * decision relay-and-metronome-read-apart-at-rest
 */
public final class PrismCrystal {

    /** The one upright, flat-faced column. */
    public static final List<CrystalCluster.Prism> PRISMS = List.of(PrismColumn.PRISM);

    /** The prism's milky quartz texture on the block atlas. */
    public static final Identifier SPRITE = Identifier.fromNamespaceAndPath(Goo.MODID, "block/prism");

    private static final double PIXELS_PER_BLOCK = 16;
    /** The sprite's frame: one texel of its 16 on each edge. */
    private static final float FRAME_SHARE = 1f / 16f;
    /** The blob's cube half-width in model pixels, the shape the morph starts from. */
    static final double BLOB_HALF_WIDTH = GooFlightRenderer.SHELL_HW * PIXELS_PER_BLOCK;
    /** The cube's corners stand off its axis by its half-width across the diagonal. */
    private static final double BLOB_CORNER_RADIUS = BLOB_HALF_WIDTH * Math.sqrt(2);
    /**
     * Points around the rim, every 15 degrees, so the square's corners (45 degrees off
     * a side) and the hexagon's (every 60) each fall on one.
     */
    static final int RIM_SLICES = 24;
    private static final double HEXAGON_SIDE = Math.PI / 3;
    private static final double SQUARE_SIDE = Math.PI / 2;
    private static final double SQUARE_CORNER = Math.PI / 4;
    private static final double HALF_TURN = 0.5;
    /** The morph's rings, from the base's center up to the top's center. */
    private static final int BASE_CENTER = 0;
    private static final int BOTTOM_RIM = 1;
    private static final int TOP_RIM = 2;
    private static final int TOP_CENTER = 3;
    private static final int RINGS = 4;

    private PrismCrystal() {
    }

    /**
     * The sides a combined column stands with at rest: six as the plain prism's,
     * or four, the square column Relay and Reflector fold into, its orthogonal
     * sides denoting how they link to other prisms
     * (decision relay-and-metronome-read-apart-at-rest).
     */
    public enum ColumnSides {
        /** The plain six-sided column. */
        SIX,
        /** The four-sided column. */
        FOUR
    }

    /**
     * Draws the column with its resting sides; a four-sided column part way through
     * its fold from six sides draws the fold.
     *
     * @param poseStack     the pose, turned to stand on the landing face
     * @param nodeCollector the submit collector
     * @param sides         the column's resting sides
     * @param foldShare     how far a four-sided column has folded from six sides, 0 to 1
     * @param look          the column's look
     * @param light         the packed light
     */
    public static void submitColumn(PoseStack poseStack, SubmitNodeCollector nodeCollector, ColumnSides sides,
                                    double foldShare, CrystalClusterSubmitter.Look look, int light) {
        if (sides == ColumnSides.SIX) {
            CrystalClusterSubmitter.submit(poseStack, nodeCollector, PRISMS, look, light);
        } else {
            CrystalClusterSubmitter.submitFaces(poseStack, nodeCollector, foldFaces(foldShare), look, light);
        }
    }

    /**
     * The column part way from six sides to four, in model pixels on
     * {@link CrystalCluster}'s base point along +y: each rim point slides from the
     * hexagon to the square of the same corner radius, the shaft and the point
     * keeping their heights. One of the model transformations
     * (decision model-transformation-is-one-animation).
     *
     * @param fold how far the column has folded, 0 for six sides, 1 for four
     * @return the faces, each four corners wound outward
     */
    public static List<Vec3[]> foldFaces(double fold) {
        CrystalCluster.Prism prism = PRISMS.getFirst();
        double shaft = prism.length() - prism.tipLength();
        Vec3[][] rings = new Vec3[RINGS][RIM_SLICES + 1];
        for (int slice = 0; slice <= RIM_SLICES; slice++) {
            double angle = Math.TAU * slice / RIM_SLICES;
            double hexagon = prism.radius() * rimShare(angle, HEXAGON_SIDE, 0);
            double square = prism.radius() * rimShare(angle, SQUARE_SIDE, 0);
            rings[BASE_CENTER][slice] = morphPoint(angle, 0, 0, 0, 0, fold);
            rings[BOTTOM_RIM][slice] = morphPoint(angle, hexagon, 0, square, 0, fold);
            rings[TOP_RIM][slice] = morphPoint(angle, hexagon, shaft, square, shaft, fold);
            rings[TOP_CENTER][slice] = morphPoint(angle, 0, prism.length(), 0, prism.length(), fold);
        }
        return facesBetween(rings);
    }

    /**
     * The column's look: the prism sprite's interior, untinted at the crystal alpha.
     * The sprite's one-texel frame is more opaque than its interior, and the column
     * tiles the sprite, so the frame would stand as a two-texel band at every tile
     * seam; the look samples inside it.
     *
     * @param uv the prism sprite's rectangle on the block atlas
     * @return the look
     */
    public static CrystalClusterSubmitter.Look lookOf(GooRenderUtil.UvRect uv) {
        float insetU = (uv.u1() - uv.u0()) * FRAME_SHARE;
        float insetV = (uv.v1() - uv.v0()) * FRAME_SHARE;
        GooRenderUtil.UvRect interior = new GooRenderUtil.UvRect(uv.u0() + insetU, uv.v0() + insetV,
                uv.u1() - insetU, uv.v1() - insetV);
        return new CrystalClusterSubmitter.Look(interior, ARGB.color(CrystalClusterSubmitter.CRYSTAL_ALPHA,
                GooRenderUtil.OPAQUE_WHITE));
    }

    /**
     * @return the column's look, resolved from the block atlas
     */
    public static CrystalClusterSubmitter.Look look() {
        return lookOf(GooSubmitter.spriteUv(GooSubmitter.blockSprite(SPRITE)));
    }

    /**
     * @param look  a look
     * @param share the share of its opacity to keep, 0 to 1
     * @return the look faded to that share
     */
    public static CrystalClusterSubmitter.Look fade(CrystalClusterSubmitter.Look look, float share) {
        int alpha = Math.round(ARGB.alpha(look.color()) * Math.clamp(share, 0f, 1f));
        return new CrystalClusterSubmitter.Look(look.uv(), ARGB.color(alpha, look.color()));
    }

    /**
     * The blob part way into the column, in model pixels on {@link CrystalCluster}'s base
     * point along +y: each point of the blob's cube slides to its place on the column.
     * Four rings run from the base's center through the bottom rim and the top rim to
     * the top's center, which on the column are the base, the shaft's top and the point.
     *
     * @param morph how far the blob has become the column, 0 for the cube, 1 for the column
     * @return the faces, each four corners wound outward
     */
    public static List<Vec3[]> morphFaces(double morph) {
        CrystalCluster.Prism prism = PRISMS.getFirst();
        double shaft = prism.length() - prism.tipLength();
        double blobBottom = 0;
        double blobTop = BLOB_HALF_WIDTH + BLOB_HALF_WIDTH;
        Vec3[][] rings = new Vec3[RINGS][RIM_SLICES + 1];
        for (int slice = 0; slice <= RIM_SLICES; slice++) {
            double angle = Math.TAU * slice / RIM_SLICES;
            double square = BLOB_CORNER_RADIUS * rimShare(angle, SQUARE_SIDE, SQUARE_CORNER);
            double hexagon = prism.radius() * rimShare(angle, HEXAGON_SIDE, 0);
            rings[BASE_CENTER][slice] = morphPoint(angle, 0, blobBottom, 0, 0, morph);
            rings[BOTTOM_RIM][slice] = morphPoint(angle, square, blobBottom, hexagon, 0, morph);
            rings[TOP_RIM][slice] = morphPoint(angle, square, blobTop, hexagon, shaft, morph);
            rings[TOP_CENTER][slice] = morphPoint(angle, 0, blobTop, 0, prism.length(), morph);
        }
        return facesBetween(rings);
    }

    /**
     * The quads between each of four rings and the next: base centre, bottom
     * rim, top rim and top centre, each a point per slice.
     *
     * @param rings the morph's rings, each a point per slice and the first repeated last
     * @return the quads between each ring and the next, wound outward
     */
    public static List<Vec3[]> facesBetween(Vec3[][] rings) {
        List<Vec3[]> faces = new ArrayList<>();
        for (int band = 0; band < TOP_CENTER; band++) {
            for (int slice = 0; slice < RIM_SLICES; slice++) {
                Vec3 lowerLeft = rings[band][slice];
                Vec3 lowerRight = rings[band][slice + 1];
                Vec3 upperRight = rings[band + 1][slice + 1];
                Vec3 upperLeft = rings[band + 1][slice];
                // The base band starts on its upper edge, so the center's repeat falls last and the normal holds.
                faces.add(band == BASE_CENTER ? new Vec3[] {upperRight, upperLeft, lowerLeft, lowerRight}
                        : new Vec3[] {lowerLeft, lowerRight, upperRight, upperLeft});
            }
        }
        return faces;
    }

    /**
     * A regular polygon's rim at an angle, as a share of its corner radius: 1 at a corner,
     * dipping to the cosine of half a side's turn mid-side.
     *
     * @param angle  the angle around the axis
     * @param side   the turn one side spans
     * @param corner the angle of a corner
     * @return the share
     */
    private static double rimShare(double angle, double side, double corner) {
        double half = side * HALF_TURN;
        double fromCorner = ((angle - corner) % side + side) % side;
        return Math.cos(half) / Math.cos(fromCorner - half);
    }

    /**
     * @param angle       the angle around the axis
     * @param blobRim     the point's distance from the axis on the cube
     * @param blobHeight  its height up the axis on the cube
     * @param prismRim    its distance from the axis on the column
     * @param prismHeight its height up the axis on the column
     * @param morph       how far the cube has become the column
     * @return the point, in model pixels
     */
    private static Vec3 morphPoint(double angle, double blobRim, double blobHeight, double prismRim,
                                   double prismHeight, double morph) {
        double rim = blobRim + (prismRim - blobRim) * morph;
        double height = blobHeight + (prismHeight - blobHeight) * morph;
        return new Vec3(CrystalCluster.BASE_X + Math.cos(angle) * rim, CrystalCluster.BASE_Y + height,
                CrystalCluster.BASE_Z - Math.sin(angle) * rim);
    }

    /**
     * Turns the pose so the column, which {@link CrystalClusterSubmitter} stands on
     * {@link CrystalCluster}'s base point along +y, stands on the center of the
     * landing face and points along {@code facing} into the cell.
     *
     * @param poseStack the pose at the cell's corner
     * @param facing    the prism's facing, the face it landed on being the opposite one
     */
    public static void standOnLandingFace(PoseStack poseStack, Direction facing) {
        poseStack.mulPose(PrismColumn.placement(facing));
    }
}
