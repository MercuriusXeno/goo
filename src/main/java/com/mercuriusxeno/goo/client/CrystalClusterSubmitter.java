package com.mercuriusxeno.goo.client;

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
 * standing on {@link CrystalCluster}'s base point.
 */
public final class CrystalClusterSubmitter {

    /** The crystal's alpha: a little see-through, as quartz is. */
    private static final int CRYSTAL_ALPHA = 0xE0;
    private static final int SIDES = 6;
    private static final double SIDE_ANGLE = Math.PI * 2 / SIDES;
    private static final double PIXEL = 1.0 / 16.0;
    /** A sprite is 16 texels across. */
    private static final double SPRITE_TEXELS = 16;
    /** The height, in model pixels, v counts down from above the crystal's base. */
    private static final double SPRITE_PIXELS = 16;
    /** Texels per model pixel: half the texture's first scale (operator ruling). */
    private static final double TEXELS_PER_PIXEL = 2;
    private static final int TRIANGLE = 3;
    /**
     * A face turned evenly between x and z reads x's plane: without the margin, rounding
     * in its normal flips the choice frame to frame as the crystal grows, and the face flickers.
     */
    private static final double AXIS_TIE = 1e-6;

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
                emitPrism(ctx, prism, look.color(), look.uv());
            }
        });
    }

    /**
     * A point of a face, in model pixels, with its texture place in texels.
     *
     * @param pos the point, in model pixels
     * @param u   texels along the face's plane
     * @param v   texels down the face's plane
     */
    record TexelPoint(Vec3 pos, double u, double v) {
    }

    /**
     * Cuts a face along the sprite's tile edges, each piece carrying texels within one
     * sprite, so the goo texture repeats across the crystal as it does across blocks,
     * never clamped or stretched and never jumping as the crystal grows (operator
     * rulings: tile, don't stretch; half the texture's first scale). A face reads its
     * place in the block on its dominant plane: turned up, x and z; turned to x, z and
     * height; turned to z, x and height.
     *
     * @param corners the face's corners in winding order, in model pixels
     * @param normal  the face's normal
     * @return the pieces, each a convex polygon with texels in [0, 16]
     */
    static List<List<TexelPoint>> tilePieces(Vec3[] corners, Vec3 normal) {
        List<TexelPoint> face = new ArrayList<>();
        for (Vec3 corner : corners) {
            double[] texel = project(corner, normal);
            TexelPoint point = new TexelPoint(corner, texel[0], texel[1]);
            if (face.isEmpty() || !face.getLast().pos().equals(corner)) {
                face.add(point);
            }
        }
        if (face.size() > 1 && face.getFirst().pos().equals(face.getLast().pos())) {
            face.removeLast();
        }
        List<List<TexelPoint>> pieces = List.of(face);
        pieces = cutAlong(pieces, true);
        pieces = cutAlong(pieces, false);
        List<List<TexelPoint>> local = new ArrayList<>();
        for (List<TexelPoint> piece : pieces) {
            if (piece.size() >= TRIANGLE) {
                local.add(intoOneSprite(piece));
            }
        }
        return local;
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
     * @param corner a corner, in model pixels
     * @param normal the face's normal
     * @return the corner's {u, v} in texels on the face's dominant plane, v counted down from the top
     */
    private static double[] project(Vec3 corner, Vec3 normal) {
        double down = SPRITE_PIXELS - (corner.y - CrystalCluster.BASE_Y);
        double[] planar;
        if (Math.abs(normal.y) >= Math.abs(normal.x) - AXIS_TIE && Math.abs(normal.y) >= Math.abs(normal.z) - AXIS_TIE) {
            planar = new double[] {corner.x, corner.z};
        } else {
            planar = Math.abs(normal.x) >= Math.abs(normal.z) - AXIS_TIE
                    ? new double[] {corner.z, down} : new double[] {corner.x, down};
        }
        return new double[] {planar[0] * TEXELS_PER_PIXEL, planar[1] * TEXELS_PER_PIXEL};
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
     * @return the piece with its texels shifted by whole sprites into [0, 16]
     */
    private static List<TexelPoint> intoOneSprite(List<TexelPoint> piece) {
        double sumU = 0;
        double sumV = 0;
        for (TexelPoint point : piece) {
            sumU += point.u();
            sumV += point.v();
        }
        double shiftU = Math.floor(sumU / piece.size() / SPRITE_TEXELS) * SPRITE_TEXELS;
        double shiftV = Math.floor(sumV / piece.size() / SPRITE_TEXELS) * SPRITE_TEXELS;
        List<TexelPoint> local = new ArrayList<>();
        for (TexelPoint point : piece) {
            local.add(new TexelPoint(point.pos(), clampToSprite(point.u() - shiftU), clampToSprite(point.v() - shiftV)));
        }
        return local;
    }

    private static double clampToSprite(double texels) {
        return Math.max(0, Math.min(SPRITE_TEXELS, texels));
    }

    /**
     * Emits one prism's six sides and its six tip faces.
     *
     * @param ctx   the render context
     * @param prism the prism
     * @param color the tint
     * @param uv    the type's sprite rectangle, tiled onto each face by {@link #blockUv}
     */
    private static void emitPrism(RenderContext ctx, CrystalCluster.Prism prism, int color, GooRenderUtil.UvRect uv) {
        for (Vec3[] face : prismFaces(prism)) {
            emitQuad(ctx, color, uv, face);
        }
    }

    /**
     * A prism's faces in model pixels, each four corners in winding order: the six sides,
     * then the six tip faces, each tip face closing on the tip twice.
     *
     * @param prism the prism
     * @return the faces
     */
    static List<Vec3[]> prismFaces(CrystalCluster.Prism prism) {
        double tilt = Math.toRadians(prism.tilt());
        double yaw = Math.toRadians(prism.yaw());
        Vec3 axis = new Vec3(Math.sin(tilt) * Math.sin(yaw), Math.cos(tilt), Math.sin(tilt) * Math.cos(yaw));
        Vec3 across = tilt == 0 ? new Vec3(1, 0, 0) : axis.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 along = axis.cross(across);
        Vec3 base = new Vec3(CrystalCluster.BASE_X, CrystalCluster.BASE_Y, CrystalCluster.BASE_Z);
        Vec3 tip = base.add(axis.scale(prism.length()));
        Vec3 shaft = axis.scale(prism.length() - prism.tipLength());
        Vec3[] bottom = new Vec3[SIDES];
        Vec3[] top = new Vec3[SIDES];
        for (int k = 0; k < SIDES; k++) {
            Vec3 rim = across.scale(Math.cos(k * SIDE_ANGLE) * prism.radius())
                    .add(along.scale(Math.sin(k * SIDE_ANGLE) * prism.radius()));
            bottom[k] = base.add(rim);
            top[k] = bottom[k].add(shaft);
        }
        List<Vec3[]> faces = new ArrayList<>();
        for (int k = 0; k < SIDES; k++) {
            int next = (k + 1) % SIDES;
            faces.add(new Vec3[] {bottom[k], bottom[next], top[next], top[k]});
            faces.add(new Vec3[] {top[k], top[next], tip, tip});
        }
        return faces;
    }

    /**
     * @param corners a face's corners in winding order
     * @return the face's unit normal
     */
    static Vec3 faceNormal(Vec3[] corners) {
        return corners[1].subtract(corners[0]).cross(corners[corners.length - 1].subtract(corners[0])).normalize();
    }

    /**
     * Emits one face, cut at the sprite's tile edges, each piece as a fan of quads,
     * lit by the face's own normal.
     *
     * @param ctx     the render context
     * @param color   the tint
     * @param sprite  the type's sprite rectangle
     * @param corners the four corners in winding order
     */
    private static void emitQuad(RenderContext ctx, int color, GooRenderUtil.UvRect sprite, Vec3[] corners) {
        Vec3 normal = faceNormal(corners);
        for (List<TexelPoint> piece : tilePieces(corners, normal)) {
            for (int i = 1; i + 1 < piece.size(); i++) {
                TexelPoint[] quad = {piece.getFirst(), piece.get(i), piece.get(i + 1), piece.get(i + 1)};
                for (TexelPoint point : quad) {
                    Vec3 corner = point.pos().scale(PIXEL);
                    float[] uv = atlasUv(sprite, point);
                    ctx.vertexColored(color, (float) corner.x, (float) corner.y, (float) corner.z, uv[0], uv[1],
                            (float) normal.x, (float) normal.y, (float) normal.z);
                }
            }
        }
    }
}
