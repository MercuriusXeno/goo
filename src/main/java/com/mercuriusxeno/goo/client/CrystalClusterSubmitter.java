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
        float[][] corners = quadUv(look.uv());
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(), (pose, c) -> {
            RenderContext ctx = new RenderContext(pose, c, light);
            for (CrystalCluster.Prism prism : prisms) {
                emitPrism(ctx, prism, look.color(), corners);
            }
        });
    }

    /**
     * Maps a sprite's rectangle onto a quad's corners in winding order: bottom left,
     * bottom right, top right, top left, so every face shows the whole sprite upright.
     *
     * @param uv the sprite's rectangle on the atlas
     * @return {u, v} for each of the four corners
     */
    static float[][] quadUv(GooRenderUtil.UvRect uv) {
        return new float[][] {{uv.u0(), uv.v1()}, {uv.u1(), uv.v1()}, {uv.u1(), uv.v0()}, {uv.u0(), uv.v0()}};
    }

    /**
     * Emits one prism's six sides and its six tip faces.
     *
     * @param ctx   the render context
     * @param prism the prism
     * @param color the tint
     * @param uv    the sprite UV at each quad corner, from {@link #quadUv}
     */
    private static void emitPrism(RenderContext ctx, CrystalCluster.Prism prism, int color, float[][] uv) {
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
     * @param uv      the sprite UV at each corner
     * @param corners the four corners in winding order
     */
    private static void emitQuad(RenderContext ctx, int color, float[][] uv, Vec3[] corners) {
        Vec3 normal = corners[1].subtract(corners[0]).cross(corners[corners.length - 1].subtract(corners[0])).normalize();
        for (int i = 0; i < corners.length; i++) {
            Vec3 corner = corners[i].scale(PIXEL);
            ctx.vertexColored(color, (float) corner.x, (float) corner.y, (float) corner.z, uv[i][0], uv[i][1],
                    (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }
}
