package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
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
    /** A sprite is 16 texture pixels across. */
    private static final double SPRITE_PIXELS = 16;

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
     * Maps a face onto the sprite by where its corners sit in the block, one texture
     * pixel per model pixel, so the goo texture tiles continuously across the crystal
     * as it does across a block face (operator ruling: tile, don't stretch). Each face
     * projects on its dominant axis: a face turned up reads x and z, one turned to x
     * reads z and height, one turned to z reads x and height. A face is shifted whole
     * into one sprite and clamped to its edge, so it never wraps mid-face.
     *
     * @param uv      the sprite's rectangle on the atlas
     * @param corners the face's corners, in model pixels
     * @param normal  the face's normal
     * @return {u, v} for each corner
     */
    static float[][] blockUv(GooRenderUtil.UvRect uv, Vec3[] corners, Vec3 normal) {
        double[][] planar = new double[corners.length][];
        for (int i = 0; i < corners.length; i++) {
            planar[i] = project(corners[i], normal);
        }
        double shiftU = spriteShift(planar, 0);
        double shiftV = spriteShift(planar, 1);
        float[][] mapped = new float[corners.length][];
        for (int i = 0; i < corners.length; i++) {
            double u = clampToSprite(planar[i][0] - shiftU);
            double v = clampToSprite(planar[i][1] - shiftV);
            mapped[i] = new float[] {uv.u0() + (uv.u1() - uv.u0()) * (float) (u / SPRITE_PIXELS),
                uv.v0() + (uv.v1() - uv.v0()) * (float) (v / SPRITE_PIXELS)};
        }
        return mapped;
    }

    /**
     * @param corner a corner, in model pixels
     * @param normal the face's normal
     * @return the corner's {u, v} in pixels on the face's dominant plane, v counted down from the top
     */
    private static double[] project(Vec3 corner, Vec3 normal) {
        double down = SPRITE_PIXELS - (corner.y - CrystalCluster.BASE_Y);
        if (Math.abs(normal.y) >= Math.abs(normal.x) && Math.abs(normal.y) >= Math.abs(normal.z)) {
            return new double[] {corner.x, corner.z};
        }
        return Math.abs(normal.x) >= Math.abs(normal.z)
                ? new double[] {corner.z, down} : new double[] {corner.x, down};
    }

    /**
     * @param planar the face's projected corners
     * @param axis   0 for u, 1 for v
     * @return the whole-sprite shift that brings the face's lowest corner into the sprite
     */
    private static double spriteShift(double[][] planar, int axis) {
        double lowest = Double.MAX_VALUE;
        for (double[] point : planar) {
            lowest = Math.min(lowest, point[axis]);
        }
        return Math.floor(lowest / SPRITE_PIXELS) * SPRITE_PIXELS;
    }

    private static double clampToSprite(double pixels) {
        return Math.max(0, Math.min(SPRITE_PIXELS, pixels));
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
        for (int k = 0; k < SIDES; k++) {
            int next = (k + 1) % SIDES;
            emitQuad(ctx, color, uv, new Vec3[] {bottom[k], bottom[next], top[next], top[k]});
            emitQuad(ctx, color, uv, new Vec3[] {top[k], top[next], tip, tip});
        }
    }

    /**
     * Emits one quad, its corners in model pixels, lit by its own normal.
     *
     * @param ctx     the render context
     * @param color   the tint
     * @param sprite  the type's sprite rectangle
     * @param corners the four corners in winding order
     */
    private static void emitQuad(RenderContext ctx, int color, GooRenderUtil.UvRect sprite, Vec3[] corners) {
        Vec3 normal = corners[1].subtract(corners[0]).cross(corners[corners.length - 1].subtract(corners[0])).normalize();
        float[][] uv = blockUv(sprite, corners, normal);
        for (int i = 0; i < corners.length; i++) {
            Vec3 corner = corners[i].scale(PIXEL);
            ctx.vertexColored(color, (float) corner.x, (float) corner.y, (float) corner.z, uv[i][0], uv[i][1],
                    (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }
}
