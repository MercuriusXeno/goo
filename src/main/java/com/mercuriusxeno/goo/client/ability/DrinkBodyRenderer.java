package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ber.MeltMesh;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import java.util.List;

/**
 * Draws a block flowing into its stream: the block's own faces, finely
 * divided, carried along its path like taffy as its lump moves toward the
 * hand, in the block's own textures going molten, drawn solid and lit by the
 * air in front of the block, with its goo types roiling over them through
 * the vats' mingle shader in blotches that grow along the block's route, as
 * the stream does.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
final class DrinkBodyRenderer {

    /** The most goo types layered over one block; past three the blotches read as noise. */
    private static final int MAX_LAYERS = 3;
    /** Noise cells across the block for the molten pull of its textures. */
    private static final double MOLTEN_SCALE = 2.5;
    /** How fast the molten pull churns, in noise cells a tick. */
    private static final double MOLTEN_CHURN = 0.015;
    private static final double HALF = 0.5;
    private static final long MOLTEN_SALT = 0x2B7E_1516L;
    private static final long MOLTEN_V_SALT = 0x4D3C_7E9AL;

    private DrinkBodyRenderer() {
    }

    /**
     * One block's flow this frame.
     *
     * @param stream its stream in the drink's tree
     * @param lump   its lump
     * @param now    the game time, with the partial tick
     * @param camera the camera's world position
     * @param light  the light in front of the block, which lights it
     */
    record Flowing(DrinkTree.Stream stream, DrinkBody.Lump lump, double now, Vec3 camera, int light) {

        BlockPos pos() {
            return stream.block().pos();
        }

        /**
         * @param local a point of the block, block-local
         * @return the point in the world, where it stood
         */
        Vec3 worldOf(Vec3 local) {
            return Vec3.atLowerCornerOf(pos()).add(local);
        }

        /**
         * @param point a point of a face
         * @return the share of the block's whole route to the glove it stands at now, which grows its goo
         */
        float routeShareOf(MeltMesh.FacePoint point) {
            Vec3 world = worldOf(new Vec3(point.x(), point.y(), point.z()));
            return (float) (lump.distanceOf(DrinkBody.alongOf(world, stream.path())) / stream.routeLength());
        }

        /**
         * @param point  a point of a face
         * @param normal the unit normal of its face
         * @return where it stands and faces, about the camera
         */
        DrinkBody.Place placeOf(MeltMesh.FacePoint point, Vec3 normal) {
            DrinkBody.Place place = DrinkBody.placeOf(worldOf(new Vec3(point.x(), point.y(), point.z())), normal, lump);
            return new DrinkBody.Place(place.point().subtract(camera), place.normal());
        }

        /**
         * @param point  a point of a face
         * @param sprite the face's sprite
         * @return its texture u, pulled about the sprite so the texture goes molten
         */
        float moltenU(MeltMesh.FacePoint point, GooRenderUtil.UvRect sprite) {
            double pull = DrinkStream.TEXTURE_WARP * (sprite.u1() - sprite.u0());
            return (float) Math.clamp(point.u() + pull * (field(point, MOLTEN_SALT) - HALF), sprite.u0(), sprite.u1());
        }

        float moltenV(MeltMesh.FacePoint point, GooRenderUtil.UvRect sprite) {
            double pull = DrinkStream.TEXTURE_WARP * (sprite.v1() - sprite.v0());
            return (float) Math.clamp(point.v() + pull * (field(point, MOLTEN_V_SALT) - HALF), sprite.v0(),
                    sprite.v1());
        }

        private double field(MeltMesh.FacePoint point, long salt) {
            return MeltMeshNoise.smooth(point.x() * MOLTEN_SCALE, point.y() * MOLTEN_SCALE,
                    point.z() * MOLTEN_SCALE + now * MOLTEN_CHURN, stream.block().seed() + salt);
        }
    }

    /**
     * Submits the block's faces flowing, solid, then each goo type's blotches over them.
     *
     * @param event   the custom geometry submit event
     * @param level   the client level
     * @param state   the block the stream is
     * @param goo     the goo it becomes
     * @param flowing its flow this frame
     */
    static void submit(SubmitCustomGeometryEvent event, ClientLevel level, BlockState state, MingledGoo goo,
                       Flowing flowing) {
        List<BakedQuad> quads = MeltMesh.quadsOf(state, level, flowing.pos());
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooSubmitter.solidOnBlockAtlas(),
                (pose, consumer) -> {
                    RenderContext ctx = new RenderContext(pose, consumer, flowing.light());
                    for (BakedQuad quad : quads) {
                        emitFace(ctx, flowing, quad, MeltMesh.tintOf(state, level, flowing.pos(), quad));
                    }
                });
        for (int layer = 0; layer < Math.min(goo.types().size(), MAX_LAYERS); layer++) {
            submitGoo(event, flowing, quads, goo, layer);
        }
    }

    private static void submitGoo(SubmitCustomGeometryEvent event, Flowing flowing, List<BakedQuad> quads,
                                  MingledGoo goo, int layer) {
        GooRenderUtil.UvRect sprite = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(goo.types().get(layer)));
        int tint = GooSubmitter.fluidTint(goo.types().get(layer));
        double lift = DrinkRenderer.LAYER_STEP * (layer + 1);
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooRenderTypes.gooFluidSurface(
                Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location()),
                (pose, consumer) -> {
                    for (BakedQuad quad : quads) {
                        emitGooFace(pose, consumer, flowing, quad, new Layer(goo, layer, sprite, lift, tint));
                    }
                });
    }

    /**
     * One goo type's layer over the block.
     *
     * @param goo    the goo
     * @param index  the type's index, largest first
     * @param sprite the type's sprite
     * @param lift   how far the layer stands off the block
     * @param tint   the type's tint
     */
    private record Layer(MingledGoo goo, int index, GooRenderUtil.UvRect sprite, double lift, int tint) {
    }

    /**
     * Emits one face of the block flowing: every cell's corners carried to
     * their places, in the block's own texture going molten.
     *
     * @param ctx     the render context
     * @param flowing the block's flow
     * @param quad    the face
     * @param tint    the face's tint
     */
    private static void emitFace(RenderContext ctx, Flowing flowing, BakedQuad quad, int tint) {
        Vec3 normal = Vec3.atLowerCornerOf(quad.direction().getUnitVec3i());
        GooRenderUtil.UvRect sprite = MeltMesh.spriteOf(quad);
        for (MeltMesh.FacePoint[] cell : MeltMesh.cellsOf(quad)) {
            for (MeltMesh.FacePoint point : cell) {
                DrinkBody.Place place = flowing.placeOf(point, normal);
                ctx.vertexColored(ARGB.multiply(tint, point.color()), (float) place.point().x,
                        (float) place.point().y, (float) place.point().z, flowing.moltenU(point, sprite),
                        flowing.moltenV(point, sprite), (float) place.normal().x, (float) place.normal().y,
                        (float) place.normal().z);
            }
        }
    }

    /**
     * Emits one goo type's blotches over a face flowing: each cell on the
     * mingle shader with the band of the goo's reach there, its sprite laid
     * once across the face, lifted off the block so the layers never fight.
     *
     * @param pose     the pose
     * @param consumer the mingle buffer
     * @param flowing  the block's flow
     * @param quad     the face
     * @param layer    the goo layer
     */
    private static void emitGooFace(PoseStack.Pose pose, VertexConsumer consumer, Flowing flowing, BakedQuad quad,
                                    Layer layer) {
        Vec3 normal = Vec3.atLowerCornerOf(quad.direction().getUnitVec3i());
        GooRenderUtil.UvRect face = MeltMesh.spriteOf(quad);
        for (MeltMesh.FacePoint[] cell : MeltMesh.cellsOf(quad)) {
            float reach = 0f;
            for (MeltMesh.FacePoint point : cell) {
                reach += flowing.routeShareOf(point) / cell.length;
            }
            RenderContext ctx = RenderContext.banded(pose, consumer, layer.tint(), DrinkRenderer.bandOf(layer.goo(),
                    layer.index(), reach * DrinkRenderer.GOO_REACH));
            for (MeltMesh.FacePoint point : cell) {
                DrinkBody.Place place = flowing.placeOf(point, normal);
                Vec3 lifted = place.point().add(place.normal().scale(layer.lift()));
                float s = (point.u() - face.u0()) / Math.max(Float.MIN_NORMAL, face.u1() - face.u0());
                float t = (point.v() - face.v0()) / Math.max(Float.MIN_NORMAL, face.v1() - face.v0());
                ctx.vertexColored(layer.tint(), (float) lifted.x, (float) lifted.y, (float) lifted.z,
                        layer.sprite().u0() + (layer.sprite().u1() - layer.sprite().u0()) * s,
                        layer.sprite().v0() + (layer.sprite().v1() - layer.sprite().v0()) * t,
                        (float) place.normal().x, (float) place.normal().y, (float) place.normal().z);
            }
        }
    }
}
