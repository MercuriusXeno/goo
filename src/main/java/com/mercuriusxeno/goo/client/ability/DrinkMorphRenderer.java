package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.ber.MeltMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Draws a block turning into its stream: the block's own faces, finely
 * divided, each point bent toward its place on the stream as its turn comes,
 * keeping the block's textures, which go molten as they turn, tinting toward
 * its goo's colour and growing patches of its goo types by the mingle noise
 * along the block's route, as the stream does.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
final class DrinkMorphRenderer {

    /** How far the block's colour goes toward its goo's where the mingle has fully formed. */
    static final float TINT_BLEND = 0.7f;
    /** The most goo types layered over one block; past three the patches read as noise. */
    private static final int MAX_LAYERS = 3;
    /** Noise cells across the block for the molten pull of its textures. */
    private static final double MOLTEN_SCALE = 2.5;
    /** How fast the molten pull churns, in noise cells a tick. */
    private static final double MOLTEN_CHURN = 0.015;
    private static final double HALF = 0.5;
    private static final long MOLTEN_SALT = 0x2B7E_1516L;
    private static final long MOLTEN_V_SALT = 0x4D3C_7E9AL;

    private DrinkMorphRenderer() {
    }

    /**
     * One block's turn this frame.
     *
     * @param stream   its stream in the drink's tree
     * @param progress how far its drain has gone, 0 to 1
     * @param now      the game time, with the partial tick
     * @param camera   the camera's world position
     */
    record Turning(DrinkTree.Stream stream, double progress, double now, Vec3 camera) {

        BlockPos pos() {
            return stream.block().pos();
        }

        /**
         * @param local a point of the block, block-local
         * @return the point in the world
         */
        Vec3 worldOf(Vec3 local) {
            return Vec3.atLowerCornerOf(pos()).add(local);
        }

        /**
         * @param local a point of the block, block-local
         * @return how far it has turned into the stream
         */
        double turnedAt(Vec3 local) {
            return DrinkMorph.turned(local, DrinkMorph.depthOf(worldOf(local), stream.path()), progress,
                    stream.block().seed());
        }

        /**
         * @param local a point of the block, block-local
         * @return the share of the block's whole route to the glove it stands at, which forms its goo
         */
        float routeShareOf(Vec3 local) {
            return (float) (DrinkMorph.distanceAlong(worldOf(local), stream.path()) / stream.routeLength());
        }

        /**
         * @param local  a point of the block, block-local
         * @param normal the unit normal of its face
         * @param turned how far it has turned
         * @return where it stands and faces, about the camera
         */
        DrinkMorph.Place placeOf(Vec3 local, Vec3 normal, double turned) {
            DrinkMorph.Place place = DrinkMorph.placeOf(worldOf(local), normal, stream.path(),
                    share -> DrinkTree.ring(stream, share, now), turned);
            return new DrinkMorph.Place(place.point().subtract(camera), place.normal());
        }

        /**
         * @param point  a point of a face
         * @param sprite the face's sprite
         * @param turned how far the point has turned
         * @return its texture u, pulled about the sprite as far as it has turned, so the texture goes molten
         */
        float moltenU(MeltMesh.FacePoint point, GooRenderUtil.UvRect sprite, double turned) {
            double pull = DrinkStream.TEXTURE_WARP * turned * (sprite.u1() - sprite.u0());
            return (float) Math.clamp(point.u() + pull * (field(point, MOLTEN_SALT) - HALF), sprite.u0(), sprite.u1());
        }

        float moltenV(MeltMesh.FacePoint point, GooRenderUtil.UvRect sprite, double turned) {
            double pull = DrinkStream.TEXTURE_WARP * turned * (sprite.v1() - sprite.v0());
            return (float) Math.clamp(point.v() + pull * (field(point, MOLTEN_V_SALT) - HALF), sprite.v0(),
                    sprite.v1());
        }

        private double field(MeltMesh.FacePoint point, long salt) {
            return MeltMeshNoise.smooth(point.x() * MOLTEN_SCALE, point.y() * MOLTEN_SCALE,
                    point.z() * MOLTEN_SCALE + now * MOLTEN_CHURN, stream.block().seed() + salt);
        }
    }

    /**
     * Submits the block's faces turning, then each goo type's patches over them.
     *
     * @param event   the custom geometry submit event
     * @param level   the client level
     * @param state   the block the stream is
     * @param goo     the goo it becomes
     * @param turning its turn this frame
     */
    static void submit(SubmitCustomGeometryEvent event, ClientLevel level, BlockState state, MingledGoo goo,
                       Turning turning) {
        List<BakedQuad> quads = MeltMesh.quadsOf(state, level, turning.pos());
        int light = LevelRenderer.getLightCoords(level, turning.pos());
        MeltMesh.GooLayer base = goo.types().isEmpty() ? null : new MeltMesh.GooLayer(goo.types().getFirst(), 0,
                goo.share(0));
        GooSubmitter.submitBody(event.getPoseStack(), event.getSubmitNodeCollector(), light, ctx -> {
            for (BakedQuad quad : quads) {
                emitFace(ctx, turning, quad, MeltMesh.tintOf(state, level, turning.pos(), quad), base);
            }
        });
        for (int layer = 0; layer < Math.min(goo.types().size(), MAX_LAYERS); layer++) {
            submitGoo(event, turning, quads, new MeltMesh.GooLayer(goo.types().get(layer), layer, goo.share(layer)));
        }
    }

    private static void submitGoo(SubmitCustomGeometryEvent event, Turning turning, List<BakedQuad> quads,
                                  MeltMesh.GooLayer layer) {
        GooRenderUtil.UvRect sprite = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(layer.type()));
        int tint = GooSubmitter.fluidTint(layer.type());
        TypeBand whole = new TypeBand(layer.type(), 0f, 1f, layer.index());
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooRenderTypes.gooFluidSurface(
                Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location()),
                (pose, consumer) -> {
                    RenderContext ctx = RenderContext.banded(pose, consumer, tint, whole);
                    for (BakedQuad quad : quads) {
                        emitGooFace(ctx, turning, quad, layer, sprite);
                    }
                });
    }

    /**
     * Emits one face of the block turning: every cell's corners bent to their
     * places, in the block's own texture going molten, tinted toward its goo
     * where the mingle has formed along the route.
     *
     * @param ctx     the render context
     * @param turning the block's turn
     * @param quad    the face
     * @param tint    the face's tint
     * @param base    the goo's largest type, whose mingle tints the block, or null for a block with no goo
     */
    private static void emitFace(RenderContext ctx, Turning turning, BakedQuad quad, int tint,
                                 MeltMesh.@Nullable GooLayer base) {
        Vec3 normal = Vec3.atLowerCornerOf(quad.direction().getUnitVec3i());
        GooRenderUtil.UvRect sprite = MeltMesh.spriteOf(quad);
        int gooColor = base == null ? tint : ARGB.opaque(ClientGooTypes.color(base.type()));
        for (MeltMesh.FacePoint[] cell : MeltMesh.cellsOf(quad)) {
            for (MeltMesh.FacePoint point : cell) {
                Vec3 local = new Vec3(point.x(), point.y(), point.z());
                double turned = turning.turnedAt(local);
                DrinkMorph.Place place = turning.placeOf(local, normal, turned);
                float formed = base == null ? 0f
                        : base.opacityAt(point.x(), point.y(), point.z(), turning.routeShareOf(local));
                int color = ARGB.srgbLerp(formed * TINT_BLEND, ARGB.multiply(tint, point.color()), gooColor);
                ctx.vertexColored(color, (float) place.point().x, (float) place.point().y, (float) place.point().z,
                        turning.moltenU(point, sprite, turned), turning.moltenV(point, sprite, turned),
                        (float) place.normal().x, (float) place.normal().y, (float) place.normal().z);
            }
        }
    }

    /**
     * Emits one goo type's patches over a face turning: each cell as opaque
     * at each corner as the layer is there along the route, its sprite laid
     * once across the face, lifted off the block so the layers never fight.
     *
     * @param ctx     the render context
     * @param turning the block's turn
     * @param quad    the face
     * @param layer   the goo layer
     * @param sprite  the goo's sprite
     */
    private static void emitGooFace(RenderContext ctx, Turning turning, BakedQuad quad, MeltMesh.GooLayer layer,
                                    GooRenderUtil.UvRect sprite) {
        Vec3 normal = Vec3.atLowerCornerOf(quad.direction().getUnitVec3i());
        double lift = DrinkRenderer.LAYER_STEP * (layer.index() + 1);
        for (MeltMesh.FacePoint[] cell : MeltMesh.cellsOf(quad)) {
            int[] alphas = new int[cell.length];
            boolean shows = false;
            for (int corner = 0; corner < cell.length; corner++) {
                MeltMesh.FacePoint point = cell[corner];
                Vec3 local = new Vec3(point.x(), point.y(), point.z());
                alphas[corner] = Math.round(DrinkRenderer.GOO_ALPHA * layer.opacityAt(point.x(), point.y(), point.z(),
                        turning.routeShareOf(local)));
                shows |= alphas[corner] > 0;
            }
            if (shows) {
                emitGooCell(ctx, turning, cell, alphas, normal, lift, sprite, quad);
            }
        }
    }

    private static void emitGooCell(RenderContext ctx, Turning turning, MeltMesh.FacePoint[] cell, int[] alphas,
                                    Vec3 normal, double lift, GooRenderUtil.UvRect sprite, BakedQuad quad) {
        GooRenderUtil.UvRect face = MeltMesh.spriteOf(quad);
        for (int corner = 0; corner < cell.length; corner++) {
            MeltMesh.FacePoint point = cell[corner];
            Vec3 local = new Vec3(point.x(), point.y(), point.z());
            DrinkMorph.Place place = turning.placeOf(local, normal, turning.turnedAt(local));
            Vec3 lifted = place.point().add(place.normal().scale(lift));
            float s = (point.u() - face.u0()) / Math.max(Float.MIN_NORMAL, face.u1() - face.u0());
            float t = (point.v() - face.v0()) / Math.max(Float.MIN_NORMAL, face.v1() - face.v0());
            ctx.vertexColored(ARGB.color(alphas[corner], GooRenderUtil.OPAQUE_WHITE), (float) lifted.x,
                    (float) lifted.y, (float) lifted.z, sprite.u0() + (sprite.u1() - sprite.u0()) * s,
                    sprite.v0() + (sprite.v1() - sprite.v0()) * t, (float) place.normal().x,
                    (float) place.normal().y, (float) place.normal().z);
        }
    }
}
