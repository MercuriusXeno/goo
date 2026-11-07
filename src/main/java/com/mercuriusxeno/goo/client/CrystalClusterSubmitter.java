package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.block.ability.PrismColumn;
import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Submits a quartz cluster of {@link CrystalCluster} prisms in a goo type's own
 * fluid texture (decision crystallizer-emits-chrysm): the crystal growing on the
 * crystallizer and the chrysm items draw through this one path. Each prism is a
 * hexagonal column capped by a pointed tip, in model pixels scaled to the block,
 * standing on {@link CrystalCluster}'s base point. The texture wraps each prism in
 * the prism's own frame, around its axis and up it, so neighboring faces meet on
 * the same texel and the wrap closes on itself.
 * decision diagnose-then-fix-crystal-texture-seams
 */
public final class CrystalClusterSubmitter {

    /** The crystal's alpha: a little see-through, as quartz is. */
    static final int CRYSTAL_ALPHA = 0xE0;
    private static final int SIDES = 6;
    private static final double SIDE_ANGLE = Math.PI * 2 / SIDES;
    private static final double PIXEL = 1.0 / 16.0;
    /** A sprite is 16 texels across. */
    private static final double SPRITE_TEXELS = 16;
    /** Texels per model pixel: half the texture's first scale (operator ruling). */
    private static final double TEXELS_PER_PIXEL = 2;
    private static final int TRIANGLE = 3;
    /**
     * A rounding prism's rings from base to point and slices around it: two slices a
     * side, so each side rounds as it bulges; the base's center, four rings up the
     * shaft and three up the tip.
     */
    private static final int ROUND_BANDS = 8;
    private static final int ROUND_SLICES = 12;
    private static final int SHAFT_BOTTOM_RING = 1;
    private static final int SHAFT_TOP_RING = 5;
    /** The spheroid's latitudes count up from the base, a quarter turn below its equator. */
    private static final double SOUTH_POLE = Math.PI / 2;
    /** Half a hexagon side's turn, 30 degrees. */
    private static final double HALF_SIDE = Math.PI / SIDES;
    private static final double HALF = 0.5;
    /** A corner this close to a column's axis has no angle around it of its own. */
    private static final double ON_AXIS = 1e-9;

    private CrystalClusterSubmitter() {
    }

    /**
     * How a type's crystal looks: its fluid sprite and the tint over it.
     *
     * @param uv    the sprite's rectangle on the block atlas
     * @param color the ARGB tint, white over a named sprite, the type's color over the grey base
     */
    public record Look(GooRenderUtil.UvRect uv, int color) {
    }

    /**
     * Resolves a type's crystal look from its fluid sprites, as its fluid draws:
     * the sprite the type names, untinted, or the grey base tinted by its color.
     *
     * @param type the goo type
     * @param rgb  the type's color, for the grey base
     * @return the look
     */
    public static Look lookOf(ResourceKey<GooTypeDefinition> type, int rgb) {
        GooTypeSprites.FluidSprites sprites = GooSubmitter.fluidSprites(type);
        GooRenderUtil.UvRect uv = GooSubmitter.spriteUv(GooSubmitter.blockSprite(sprites.still()));
        return new Look(uv, ARGB.color(CRYSTAL_ALPHA, sprites.tinted() ? rgb : GooRenderUtil.OPAQUE_WHITE));
    }

    /**
     * Submits the prisms in one draw on the block atlas.
     *
     * @param poseStack     the pose stack, placed so model pixels map onto the block
     * @param nodeCollector the node collector
     * @param prisms        the prisms to draw
     * @param look          the type's sprite and tint
     * @param light         the packed light
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                              List<CrystalCluster.Prism> prisms, Look look, int light) {
        if (prisms.isEmpty()) {
            return;
        }
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(), (pose, c) -> {
            RenderContext ctx = new RenderContext(pose, c, light);
            for (CrystalCluster.Prism prism : prisms) {
                for (TexelPoint[] face : wrappedFaces(prism)) {
                    emitFace(ctx, look.color(), look.uv(), face);
                }
            }
        });
    }

    /**
     * Submits faces of a column standing on {@link CrystalCluster}'s base point along
     * +y, in model pixels, each four corners in winding order, in one draw on the
     * block atlas, the texture wrapped around the column as the prism column's is.
     *
     * @param poseStack     the pose stack, placed so model pixels map onto the block
     * @param nodeCollector the node collector
     * @param faces         the faces to draw
     * @param look          the sprite and tint
     * @param light         the packed light
     */
    public static void submitFaces(PoseStack poseStack, SubmitNodeCollector nodeCollector, List<Vec3[]> faces,
                                   Look look, int light) {
        double circumference = wrapCircumference(PrismColumn.PRISM);
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(), (pose, c) -> {
            RenderContext ctx = new RenderContext(pose, c, light);
            for (Vec3[] face : faces) {
                emitFace(ctx, look.color(), look.uv(), wrapAroundColumn(face, circumference));
            }
        });
    }

    /**
     * The frame a prism stands in: its base point, its axis from base to tip and the
     * two directions across it its hexagon's rim spans.
     */
    private record Frame(Vec3 base, Vec3 axis, Vec3 across, Vec3 along) {

        static Frame of(CrystalCluster.Prism prism) {
            double tilt = Math.toRadians(prism.tilt());
            double yaw = Math.toRadians(prism.yaw());
            Vec3 axis = new Vec3(Math.sin(tilt) * Math.sin(yaw), Math.cos(tilt), Math.sin(tilt) * Math.cos(yaw));
            Vec3 across = tilt == 0 ? new Vec3(1, 0, 0) : axis.cross(new Vec3(0, 1, 0)).normalize();
            return new Frame(new Vec3(CrystalCluster.BASE_X, CrystalCluster.BASE_Y, CrystalCluster.BASE_Z), axis,
                    across, axis.cross(across));
        }

        Vec3 rim(double angle) {
            return across.scale(Math.cos(angle)).add(along.scale(Math.sin(angle)));
        }
    }

    /**
     * A point of a face, in model pixels, with its texture place in texels.
     *
     * @param pos the point, in model pixels
     * @param u   texels around the column
     * @param v   texels down the column
     */
    record TexelPoint(Vec3 pos, double u, double v) {
    }

    /**
     * @param prism the prism
     * @return its faces with their texels, the rounding mesh once it rounds, else its flat faces
     */
    static List<TexelPoint[]> wrappedFaces(CrystalCluster.Prism prism) {
        return prism.rounding() > 0 ? wrappedRoundedFaces(prism) : wrappedPrismFaces(prism);
    }

    /**
     * @param prism the prism
     * @return the rounding mesh's faces in model pixels, as {@link #wrappedRoundedFaces} wraps them
     */
    static List<Vec3[]> roundedFaces(CrystalCluster.Prism prism) {
        return positionsOf(wrappedRoundedFaces(prism));
    }

    /**
     * @param prism the prism
     * @return the flat faces in model pixels, as {@link #wrappedPrismFaces} wraps them
     */
    static List<Vec3[]> prismFaces(CrystalCluster.Prism prism) {
        return positionsOf(wrappedPrismFaces(prism));
    }

    /**
     * A rounding prism's faces (operator ruling: its six sides round into 60 degree
     * spheroid segments until what is left is a sphere). Rings run from the base's
     * center up the shaft and the tip to the point, each cut into slices; every point
     * moves from its place on the hexagonal prism toward its place on the spheroid the
     * prism's length and radius span, by the prism's rounding. Each face is four corners
     * wound outward, a band at a pole closing on the pole twice, each pole corner
     * carrying its own slice's texels so the faces around the pole meet on them.
     *
     * @param prism the prism
     * @return the faces, each corner wrapped by its slice around the axis and its arc up the meridian
     */
    static List<TexelPoint[]> wrappedRoundedFaces(CrystalCluster.Prism prism) {
        TexelPoint[][] grid = roundedGrid(prism);
        List<TexelPoint[]> faces = new ArrayList<>();
        for (int band = 0; band < ROUND_BANDS; band++) {
            for (int slice = 0; slice < ROUND_SLICES; slice++) {
                TexelPoint lowerLeft = grid[band][slice];
                TexelPoint lowerRight = grid[band][slice + 1];
                TexelPoint upperRight = grid[band + 1][slice + 1];
                TexelPoint upperLeft = grid[band + 1][slice];
                // The base band starts on its upper edge, so the pole's repeat falls last and the normal holds.
                faces.add(band == 0 ? new TexelPoint[] {upperRight, upperLeft, lowerLeft, lowerRight}
                        : new TexelPoint[] {lowerLeft, lowerRight, upperRight, upperLeft});
            }
        }
        return faces;
    }

    /**
     * @param prism the prism
     * @return its mesh's points by ring and slice, each with its texels
     */
    private static TexelPoint[][] roundedGrid(CrystalCluster.Prism prism) {
        Frame frame = Frame.of(prism);
        double circumference = wrapCircumference(prism);
        TexelPoint[][] grid = new TexelPoint[ROUND_BANDS + 1][ROUND_SLICES + 1];
        for (int slice = 0; slice <= ROUND_SLICES; slice++) {
            Vec3[] meridian = new Vec3[ROUND_BANDS + 1];
            for (int ring = 0; ring <= ROUND_BANDS; ring++) {
                meridian[ring] = roundedPoint(frame, prism, ring, slice);
            }
            double[] up = arcsFromShaftBottom(meridian);
            for (int ring = 0; ring <= ROUND_BANDS; ring++) {
                grid[ring][slice] = new TexelPoint(meridian[ring], circumference * slice / ROUND_SLICES,
                        -up[ring] * TEXELS_PER_PIXEL);
            }
        }
        return grid;
    }

    /**
     * @param meridian a slice's points from the base's center to the point
     * @return each point's distance along the meridian from the shaft's bottom ring, negative below it
     */
    private static double[] arcsFromShaftBottom(Vec3[] meridian) {
        double[] arcs = new double[meridian.length];
        arcs[0] = -meridian[0].distanceTo(meridian[SHAFT_BOTTOM_RING]);
        for (int ring = SHAFT_BOTTOM_RING + 1; ring < meridian.length; ring++) {
            arcs[ring] = arcs[ring - 1] + meridian[ring - 1].distanceTo(meridian[ring]);
        }
        return arcs;
    }

    /**
     * @param frame the prism's frame
     * @param prism the prism
     * @param ring  the ring, 0 at the base's center to {@link #ROUND_BANDS} at the point
     * @param slice the slice around the axis
     * @return the point, between the prism's and the spheroid's by the prism's rounding
     */
    private static Vec3 roundedPoint(Frame frame, CrystalCluster.Prism prism, int ring, int slice) {
        double angle = Math.TAU * slice / ROUND_SLICES;
        Vec3 rim = frame.rim(angle);
        double[] onShape = prismRing(prism, ring);
        // The hexagon's rim at this angle: the radius at a corner, cos 30 degrees of it mid-side.
        double hexagon = Math.cos(HALF_SIDE) / Math.cos(angle % SIDE_ANGLE - HALF_SIDE);
        Vec3 onPrism = frame.base().add(frame.axis().scale(onShape[0]))
                .add(rim.scale(prism.radius() * onShape[1] * hexagon));
        double latitude = Math.PI * ring / ROUND_BANDS - SOUTH_POLE;
        double halfLength = prism.length() * HALF;
        Vec3 onSpheroid = frame.base().add(frame.axis().scale(halfLength + Math.sin(latitude) * halfLength))
                .add(rim.scale(Math.cos(latitude) * prism.radius()));
        return onPrism.lerp(onSpheroid, prism.rounding());
    }

    /**
     * @param prism the prism
     * @param ring  the ring, 0 at the base's center to {@link #ROUND_BANDS} at the point
     * @return the ring's {height up the axis, share of the radius} on the flat-faced prism
     */
    private static double[] prismRing(CrystalCluster.Prism prism, int ring) {
        double shaft = prism.length() - prism.tipLength();
        if (ring == 0) {
            return new double[] {0, 0};
        }
        if (ring <= SHAFT_TOP_RING) {
            return new double[] {shaft * (ring - 1) / (SHAFT_TOP_RING - 1), 1};
        }
        double up = (double) (ring - SHAFT_TOP_RING) / (ROUND_BANDS - SHAFT_TOP_RING);
        return new double[] {shaft + prism.tipLength() * up, 1 - up};
    }

    /**
     * A prism's flat faces, each four corners in winding order: the six sides, then the
     * six tip faces, each tip face closing on the tip twice, each tip corner carrying
     * its own side's texels so the tip faces meet on them.
     *
     * @param prism the prism
     * @return the faces, each corner wrapped by its corner around the axis and its arc up the side
     */
    static List<TexelPoint[]> wrappedPrismFaces(CrystalCluster.Prism prism) {
        Frame frame = Frame.of(prism);
        double shaftLength = prism.length() - prism.tipLength();
        Vec3 tip = frame.base().add(frame.axis().scale(prism.length()));
        Vec3 shaft = frame.axis().scale(shaftLength);
        double topV = -shaftLength * TEXELS_PER_PIXEL;
        double tipV = topV - Math.hypot(prism.tipLength(), prism.radius()) * TEXELS_PER_PIXEL;
        double circumference = wrapCircumference(prism);
        TexelPoint[] bottom = new TexelPoint[SIDES + 1];
        TexelPoint[] top = new TexelPoint[SIDES + 1];
        TexelPoint[] tips = new TexelPoint[SIDES + 1];
        for (int k = 0; k <= SIDES; k++) {
            Vec3 rim = frame.base().add(frame.rim(k % SIDES * SIDE_ANGLE).scale(prism.radius()));
            double around = circumference * k / SIDES;
            bottom[k] = new TexelPoint(rim, around, 0);
            top[k] = new TexelPoint(rim.add(shaft), around, topV);
            tips[k] = new TexelPoint(tip, around, tipV);
        }
        List<TexelPoint[]> faces = new ArrayList<>();
        for (int k = 0; k < SIDES; k++) {
            faces.add(new TexelPoint[] {bottom[k], bottom[k + 1], top[k + 1], top[k]});
            faces.add(new TexelPoint[] {top[k], top[k + 1], tips[k + 1], tips[k]});
        }
        return faces;
    }

    /**
     * The texels once around a prism: its rim's length at two texels a pixel, rounded
     * to whole sprites so the wrap's closing edge meets itself (operator ruling: tile,
     * don't stretch beyond that rounding).
     *
     * @param prism the prism
     * @return the circumference in texels, at least one sprite
     */
    private static double wrapCircumference(CrystalCluster.Prism prism) {
        double rim = SIDES * prism.radius() + (Math.TAU - SIDES) * prism.radius() * prism.rounding();
        return Math.max(1, Math.round(rim * TEXELS_PER_PIXEL / SPRITE_TEXELS)) * SPRITE_TEXELS;
    }

    /**
     * Wraps a face of a column standing on {@link CrystalCluster}'s base point along +y:
     * u by its angle around the axis, v by its height. A corner on the axis takes the
     * angle of the corner beside it off the axis, the one before it where it has one,
     * so the faces around a pole meet on the same texels.
     *
     * @param face          the face's corners in winding order, in model pixels
     * @param circumference the texels once around the column, whole sprites
     * @return the face's corners with their texels
     */
    static TexelPoint[] wrapAroundColumn(Vec3[] face, double circumference) {
        int count = face.length;
        double[] angles = new double[count];
        boolean[] onAxis = new boolean[count];
        double reference = Double.NaN;
        for (int i = 0; i < count; i++) {
            double east = face[i].x - CrystalCluster.BASE_X;
            double north = CrystalCluster.BASE_Z - face[i].z;
            onAxis[i] = Math.hypot(east, north) < ON_AXIS;
            if (!onAxis[i]) {
                double angle = Math.atan2(north, east);
                // A face straddling the angle's wrap reads one side of it, so its u runs on unbroken.
                reference = Double.isNaN(reference) ? angle : reference;
                angles[i] = angle + Math.TAU * Math.round((reference - angle) / Math.TAU);
            }
        }
        TexelPoint[] wrapped = new TexelPoint[count];
        for (int i = 0; i < count; i++) {
            double angle = onAxis[i] ? poleAngle(angles, onAxis, i, reference) : angles[i];
            wrapped[i] = new TexelPoint(face[i], circumference * angle / Math.TAU,
                    -(face[i].y - CrystalCluster.BASE_Y) * TEXELS_PER_PIXEL);
        }
        return wrapped;
    }

    private static double poleAngle(double[] angles, boolean[] onAxis, int corner, double reference) {
        int before = (corner + angles.length - 1) % angles.length;
        if (!onAxis[before]) {
            return angles[before];
        }
        int after = (corner + 1) % angles.length;
        if (!onAxis[after]) {
            return angles[after];
        }
        return Double.isNaN(reference) ? 0 : reference;
    }

    private static List<Vec3[]> positionsOf(List<TexelPoint[]> faces) {
        List<Vec3[]> positions = new ArrayList<>();
        for (TexelPoint[] face : faces) {
            positions.add(positionsOf(face));
        }
        return positions;
    }

    private static Vec3[] positionsOf(TexelPoint[] face) {
        Vec3[] corners = new Vec3[face.length];
        for (int i = 0; i < face.length; i++) {
            corners[i] = face[i].pos();
        }
        return corners;
    }

    /**
     * Cuts a face along the sprite's tile edges, each piece shifted by whole sprites to
     * carry texels within one sprite, so the goo texture repeats across the crystal as
     * it does across blocks, never clamped or stretched (operator rulings: tile, don't
     * stretch; half the texture's first scale).
     *
     * @param corners the face's corners in winding order, with their texels
     * @return the pieces, each a convex polygon with texels in [0, 16]
     */
    static List<List<TexelPoint>> tilePieces(TexelPoint[] corners) {
        List<List<TexelPoint>> local = new ArrayList<>();
        for (List<TexelPoint> piece : cutAtSpriteEdges(corners)) {
            local.add(intoOneSprite(piece));
        }
        return local;
    }

    /**
     * @param corners the face's corners in winding order, with their texels
     * @return the face cut at every sprite edge its texels cross, each piece keeping the face's texels
     */
    static List<List<TexelPoint>> cutAtSpriteEdges(TexelPoint[] corners) {
        List<List<TexelPoint>> pieces = List.of(distinctCorners(corners));
        pieces = cutAlong(pieces, true);
        pieces = cutAlong(pieces, false);
        List<List<TexelPoint>> whole = new ArrayList<>();
        for (List<TexelPoint> piece : pieces) {
            if (piece.size() >= TRIANGLE) {
                whole.add(piece);
            }
        }
        return whole;
    }

    /**
     * Drops each corner that repeats the one before it and a closing corner that repeats
     * the first, so a collapsed edge yields no zero-length side. A pole corner repeating
     * with another slice's texels stays: it carries that slice's edge.
     *
     * @param corners the face's corners in winding order, with their texels
     * @return the face's distinct corners
     */
    // decision tile-pieces-dedupe-extracted
    private static List<TexelPoint> distinctCorners(TexelPoint[] corners) {
        List<TexelPoint> face = new ArrayList<>();
        for (TexelPoint corner : corners) {
            if (face.isEmpty() || !face.getLast().equals(corner)) {
                face.add(corner);
            }
        }
        if (face.size() > 1 && face.getFirst().equals(face.getLast())) {
            face.removeLast();
        }
        return face;
    }

    /**
     * @param uv    the sprite's rectangle on the atlas
     * @param point a piece's point, its texels within one sprite
     * @return the point's {u, v} on the atlas
     */
    static float[] atlasUv(GooRenderUtil.UvRect uv, TexelPoint point) {
        return new float[] {uv.u0() + (uv.u1() - uv.u0()) * (float) (point.u() / SPRITE_TEXELS),
            uv.v0() + (uv.v1() - uv.v0()) * (float) (point.v() / SPRITE_TEXELS)};
    }

    /**
     * Cuts every piece at each sprite edge its texels cross on one axis.
     *
     * @param pieces the pieces
     * @param alongU true to cut at u edges, false at v edges
     * @return the cut pieces
     */
    private static List<List<TexelPoint>> cutAlong(List<List<TexelPoint>> pieces, boolean alongU) {
        List<List<TexelPoint>> cut = new ArrayList<>();
        for (List<TexelPoint> piece : pieces) {
            double low = Double.MAX_VALUE;
            double high = -Double.MAX_VALUE;
            for (TexelPoint point : piece) {
                low = Math.min(low, texel(point, alongU));
                high = Math.max(high, texel(point, alongU));
            }
            List<TexelPoint> rest = piece;
            for (double edge = (Math.floor(low / SPRITE_TEXELS) + 1) * SPRITE_TEXELS; edge < high; edge += SPRITE_TEXELS) {
                cut.add(clip(rest, alongU, edge, true));
                rest = clip(rest, alongU, edge, false);
            }
            cut.add(rest);
        }
        return cut;
    }

    /**
     * Keeps the part of a convex polygon on one side of a texel edge.
     *
     * @param polygon the polygon
     * @param alongU  true when the edge is a u value, false a v value
     * @param edge    the edge's texel value
     * @param below   true to keep the side below the edge
     * @return the kept polygon, possibly empty
     */
    private static List<TexelPoint> clip(List<TexelPoint> polygon, boolean alongU, double edge, boolean below) {
        List<TexelPoint> kept = new ArrayList<>();
        for (int i = 0; i < polygon.size(); i++) {
            TexelPoint from = polygon.get(i);
            TexelPoint to = polygon.get((i + 1) % polygon.size());
            boolean fromIn = below == (texel(from, alongU) <= edge);
            boolean toIn = below == (texel(to, alongU) <= edge);
            if (fromIn) {
                kept.add(from);
            }
            if (fromIn != toIn) {
                double t = (edge - texel(from, alongU)) / (texel(to, alongU) - texel(from, alongU));
                kept.add(new TexelPoint(from.pos().lerp(to.pos(), t),
                        from.u() + (to.u() - from.u()) * t, from.v() + (to.v() - from.v()) * t));
            }
        }
        return kept;
    }

    private static double texel(TexelPoint point, boolean alongU) {
        return alongU ? point.u() : point.v();
    }

    /**
     * @param piece a piece lying within one tile
     * @return the piece with its texels shifted by the whole sprites of its centroid, into [0, 16]
     */
    private static List<TexelPoint> intoOneSprite(List<TexelPoint> piece) {
        TexelPoint middle = middleOf(piece);
        double shiftU = Math.floor(middle.u() / SPRITE_TEXELS) * SPRITE_TEXELS;
        double shiftV = Math.floor(middle.v() / SPRITE_TEXELS) * SPRITE_TEXELS;
        List<TexelPoint> local = new ArrayList<>();
        for (TexelPoint point : piece) {
            local.add(new TexelPoint(point.pos(), point.u() - shiftU, point.v() - shiftV));
        }
        return local;
    }

    /**
     * @param piece a piece
     * @return the mean of its points, in place and in texels
     */
    private static TexelPoint middleOf(List<TexelPoint> piece) {
        Vec3 pos = Vec3.ZERO;
        double u = 0;
        double v = 0;
        for (TexelPoint point : piece) {
            pos = pos.add(point.pos());
            u += point.u();
            v += point.v();
        }
        double share = 1.0 / piece.size();
        return new TexelPoint(pos.scale(share), u * share, v * share);
    }

    /**
     * @param corners a face's corners in winding order
     * @return the face's unit normal
     */
    static Vec3 faceNormal(Vec3[] corners) {
        return corners[1].subtract(corners[0]).cross(corners[corners.length - 1].subtract(corners[0])).normalize();
    }

    /**
     * Emits one face, cut at the sprite's tile edges, each piece as a fan of quads
     * around its middle, lit by the face's own normal. A pole corner repeats with each
     * slice's texels, and a fan from a corner would draw the edge one slice shares
     * with its neighbor in the other slice's texels; the middle's fan draws every edge
     * in its own.
     *
     * @param ctx     the render context
     * @param color   the tint
     * @param sprite  the type's sprite rectangle
     * @param corners the corners in winding order, with their texels
     */
    private static void emitFace(RenderContext ctx, int color, GooRenderUtil.UvRect sprite, TexelPoint[] corners) {
        Vec3 normal = faceNormal(positionsOf(corners));
        for (List<TexelPoint> piece : tilePieces(corners)) {
            TexelPoint middle = middleOf(piece);
            for (int i = 0; i < piece.size(); i++) {
                TexelPoint next = piece.get((i + 1) % piece.size());
                for (TexelPoint point : new TexelPoint[] {middle, piece.get(i), next, next}) {
                    Vec3 corner = point.pos().scale(PIXEL);
                    float[] uv = atlasUv(sprite, point);
                    ctx.vertexColored(color, (float) corner.x, (float) corner.y, (float) corner.z, uv[0], uv[1],
                            (float) normal.x, (float) normal.y, (float) normal.z);
                }
            }
        }
    }
}
