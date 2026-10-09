package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.ber.MeltMesh;
import com.mercuriusxeno.goo.client.ber.MeltMeshGoo;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.network.DrinkPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws every Unmake drink: each block melting like wax where it stands, and
 * its stream snaking from its face into the drinker's glove, skinned in the
 * block's own texture with patches of its goo types spreading over it as it
 * nears the glove, mingled by their shares, until it is all goo as it enters.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DrinkRenderer {

    /** The goo's alpha where a patch has fully formed over the stream. */
    static final int GOO_ALPHA = 0xEE;
    /** Sides about one ring of the stream's skin. */
    static final int SIDES = 10;
    /** How much further out each goo layer's skin stands than the one under it, so they never fight. */
    static final double LAYER_STEP = 0.008;
    /** How the liquid's place along the stream reads into the patches' field, in field blocks a block. */
    static final float PATCH_ALONG = 0.5f;
    /** The most goo types layered over one stream; past three the patches read as noise. */
    private static final int MAX_LAYERS = 3;
    private static final long MODEL_SEED = 42L;
    private static final float HALF = 0.5f;
    private static final double TWO_PI = 2 * Math.PI;
    /** Where another player's glove hangs before their eyes, in blocks. */
    private static final double GLOVE_AHEAD = 0.5;
    /** How far right of another player's look their glove hangs, in blocks. */
    private static final double GLOVE_RIGHT = 0.35;
    /** How far below another player's eyes their glove hangs, in blocks. */
    private static final double GLOVE_BELOW = 0.45;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private DrinkRenderer() {
    }

    /**
     * Submits every drink this frame.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double ticks = level.getGameTime() + partialTick;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        for (ClientDrinks.Drink drink : ClientDrinks.CLIENT.live(ticks)) {
            Entity drinker = level.getEntity(drink.playerId());
            if (drinker == null) {
                continue;
            }
            Frame frame = new Frame(gloveOf(mc, drinker, partialTick), camera, ticks);
            for (DrinkPayload.Streaming block : drink.streaming()) {
                submitBlock(event, level, block, frame);
            }
        }
    }

    /**
     * What a frame draws every block of a drink against.
     *
     * @param glove  the drinker's glove, in the world
     * @param camera the camera's world position
     * @param ticks  the game time including the partial tick
     */
    private record Frame(Vec3 glove, Vec3 camera, double ticks) {
    }

    /**
     * One stream's skin to emit.
     *
     * @param rings  the stream's rings, tail to head
     * @param light  the light where the block stands
     * @param camera the camera's world position
     */
    private record Skin(List<DrinkStream.Ring> rings, int light, Vec3 camera) {
    }

    /**
     * The colour of a point of a skin.
     */
    @FunctionalInterface
    private interface Coloring {
        /**
         * @param ring  the ring the point is on
         * @param angle the point's angle about the ring
         * @return the point's ARGB colour
         */
        int colorAt(DrinkStream.Ring ring, double angle);
    }

    /**
     * Where the drinker's goo blob sits: the local player's as the glove
     * renderer last captured it in first person, and another's at their hand.
     *
     * @param mc          the client
     * @param drinker     the drinking player
     * @param partialTick the partial tick
     * @return the glove's world position
     */
    private static Vec3 gloveOf(Minecraft mc, Entity drinker, float partialTick) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (drinker == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return GloveAim.handPosition(camera);
        }
        Vec3 look = drinker.getViewVector(partialTick);
        Vec3 right = look.cross(UP);
        right = right.lengthSqr() > 0 ? right.normalize() : Vec3.ZERO;
        return drinker.getEyePosition(partialTick).add(look.scale(GLOVE_AHEAD)).add(right.scale(GLOVE_RIGHT))
                .subtract(0, GLOVE_BELOW, 0);
    }

    private static void submitBlock(SubmitCustomGeometryEvent event, ClientLevel level, DrinkPayload.Streaming block,
                                    Frame frame) {
        BlockState seen = level.getBlockEntity(block.pos()) instanceof MeltingBlockEntity melting
                ? melting.original() : null;
        BlockState state = ClientDrinks.CLIENT.blockOf(block.pos(), seen);
        if (state == null) {
            return;
        }
        MingledGoo goo = MeltMeshGoo.of(state);
        DrinkStream.Span span = DrinkStream.span(block, frame.ticks());
        float progress = (float) Math.clamp((frame.ticks() - block.start()) / Math.max(1, block.end() - block.start()),
                0, 1);
        if (seen != null) {
            submitMelt(event, level, block.pos(), new MeltMesh.Melt(state, level, block.pos(), goo, progress,
                    (float) frame.ticks(), true), (float) span.tail(), frame.camera());
        }
        List<DrinkStream.Ring> rings = DrinkStream.rings(faceToward(block.pos(), frame.glove()), frame.glove(), span,
                frame.ticks(), block.pos().asLong());
        if (rings.size() >= DrinkStream.FEWEST_RINGS) {
            submitSkins(event, new Skin(rings, LevelRenderer.getLightCoords(level, block.pos()), frame.camera()),
                    level, block.pos(), state, goo);
        }
    }

    /**
     * Submits a stream's skins: the block's own, then each goo type's over it.
     *
     * @param event the custom geometry submit event
     * @param skin  the skin
     * @param level the client level
     * @param pos   the block
     * @param state the block the stream is
     * @param goo   the goo it becomes
     */
    private static void submitSkins(SubmitCustomGeometryEvent event, Skin skin, ClientLevel level, BlockPos pos,
                                    BlockState state, MingledGoo goo) {
        submitBlockSkin(event, skin, level, pos, state);
        for (int layer = 0; layer < Math.min(goo.types().size(), MAX_LAYERS); layer++) {
            submitGooSkin(event, skin, new MeltMesh.GooLayer(goo.types().get(layer), layer, goo.share(layer)));
        }
    }

    /**
     * The point on a block's face the stream leaves: the middle of the face
     * toward the glove.
     *
     * @param pos   the block
     * @param glove the glove
     * @return the point
     */
    static Vec3 faceToward(BlockPos pos, Vec3 glove) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 toGlove = glove.subtract(center);
        Direction face = Direction.getApproximateNearest(toGlove.x, toGlove.y, toGlove.z);
        return center.add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(HALF));
    }

    /**
     * Submits the block melting like wax where it stands, and, once its
     * stream's tail has left it, what is left of it dwindling after the tail.
     *
     * @param event  the custom geometry submit event
     * @param level  the client level
     * @param pos    the block
     * @param melt   the melt
     * @param tail   the share of the way the stream's tail has gone, 0 while the block feeds it
     * @param camera the camera's world position
     */
    private static void submitMelt(SubmitCustomGeometryEvent event, ClientLevel level, BlockPos pos,
                                   MeltMesh.Melt melt, float tail, Vec3 camera) {
        Vec3 corner = Vec3.atLowerCornerOf(pos).subtract(camera);
        float left = 1f - tail;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(corner.x + HALF, corner.y, corner.z + HALF);
        poseStack.scale(left, left, left);
        poseStack.translate(-HALF, 0f, -HALF);
        GooSubmitter.submitBody(poseStack, event.getSubmitNodeCollector(), LevelRenderer.getLightCoords(level, pos),
                ctx -> MeltMesh.emit(ctx, melt));
        poseStack.popPose();
    }

    /**
     * Submits the stream's own skin: the block's texture, laid along the liquid.
     *
     * @param event the custom geometry submit event
     * @param skin  the skin
     * @param level the client level
     * @param pos   the block
     * @param state the block the stream is
     */
    private static void submitBlockSkin(SubmitCustomGeometryEvent event, Skin skin, ClientLevel level, BlockPos pos,
                                        BlockState state) {
        BakedQuad quad = faceQuad(level, pos, state);
        if (quad == null) {
            return;
        }
        GooRenderUtil.UvRect sprite = MeltMesh.spriteOf(quad);
        int tint = tintOf(level, pos, state, quad);
        GooSubmitter.submitBody(event.getPoseStack(), event.getSubmitNodeCollector(), skin.light(),
                ctx -> emitSkin(ctx, skin, sprite, 0, (ring, angle) -> tint));
    }

    /**
     * Submits one goo type's skin over the stream: its sprite on the goo
     * surface shader, in patches that form as the liquid nears the glove.
     *
     * @param event the custom geometry submit event
     * @param skin  the skin
     * @param layer the goo layer
     */
    private static void submitGooSkin(SubmitCustomGeometryEvent event, Skin skin, MeltMesh.GooLayer layer) {
        GooRenderUtil.UvRect sprite = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(layer.type()));
        int tint = GooSubmitter.fluidTint(layer.type());
        TypeBand whole = new TypeBand(layer.type(), 0f, 1f, layer.index());
        double lift = LAYER_STEP * (layer.index() + 1);
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooRenderTypes.gooFluidSurface(
                Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location()),
                (pose, consumer) -> emitSkin(RenderContext.banded(pose, consumer, tint, whole), skin, sprite, lift,
                        (ring, angle) -> ARGB.color(gooAlpha(layer, ring, angle), tint)));
    }

    /**
     * How opaque a goo layer is at a point of the stream: its patches form by
     * the share of the way the point stands at, in a field that slides with
     * the liquid.
     *
     * @param layer the goo layer
     * @param ring  the ring the point is on
     * @param angle the point's angle about the ring
     * @return the alpha
     */
    private static int gooAlpha(MeltMesh.GooLayer layer, DrinkStream.Ring ring, double angle) {
        float around = (float) Math.cos(angle) * HALF + HALF;
        float over = (float) Math.sin(angle) * HALF + HALF;
        return Math.round(GOO_ALPHA * layer.opacityAt((float) ring.material() * PATCH_ALONG, around, over,
                (float) ring.share()));
    }

    /**
     * Emits a skin over the stream's rings: a quad between each pair of rings
     * for each side, wound to face outward, its texture sliding with the liquid.
     *
     * @param ctx      the render context
     * @param skin     the skin
     * @param sprite   the sprite laid along the liquid
     * @param lift     how far the skin stands off the stream's radius
     * @param coloring the colour of each point
     */
    private static void emitSkin(RenderContext ctx, Skin skin, GooRenderUtil.UvRect sprite, double lift,
                                 Coloring coloring) {
        List<DrinkStream.Ring> rings = skin.rings();
        for (int index = 0; index + 1 < rings.size(); index++) {
            DrinkStream.Ring near = rings.get(index);
            DrinkStream.Ring far = rings.get(index + 1);
            for (int side = 0; side < SIDES; side++) {
                double angle0 = TWO_PI * side / SIDES;
                double angle1 = TWO_PI * (side + 1) / SIDES;
                float v0 = (float) side / SIDES;
                float v1 = (float) (side + 1) / SIDES;
                emitPoint(ctx, skin, near, angle0, sprite, v0, lift, coloring);
                emitPoint(ctx, skin, near, angle1, sprite, v1, lift, coloring);
                emitPoint(ctx, skin, far, angle1, sprite, v1, lift, coloring);
                emitPoint(ctx, skin, far, angle0, sprite, v0, lift, coloring);
            }
        }
    }

    private static void emitPoint(RenderContext ctx, Skin skin, DrinkStream.Ring ring, double angle,
                                  GooRenderUtil.UvRect sprite, float v, double lift, Coloring coloring) {
        Vec3 out = ring.outAt(angle);
        Vec3 point = ring.center().add(out.scale(ring.radius() + lift)).subtract(skin.camera());
        float u = DrinkStream.textureU(ring.material());
        ctx.vertexColored(coloring.colorAt(ring, angle), (float) point.x, (float) point.y, (float) point.z,
                sprite.u0() + (sprite.u1() - sprite.u0()) * u, sprite.v0() + (sprite.v1() - sprite.v0()) * v,
                (float) out.x, (float) out.y, (float) out.z);
    }

    /**
     * The quad the stream takes its texture from: one of the block's top, or
     * failing that any of its model.
     *
     * @param level the client level
     * @param pos   the block
     * @param state the block
     * @return the quad, or null for a block with no model
     */
    private static @Nullable BakedQuad faceQuad(ClientLevel level, BlockPos pos, BlockState state) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state)
                .collectParts(level, pos, state, RandomSource.create(MODEL_SEED), parts);
        BakedQuad any = null;
        for (BlockStateModelPart part : parts) {
            List<BakedQuad> top = part.getQuads(Direction.UP);
            if (!top.isEmpty()) {
                return top.getFirst();
            }
            for (Direction side : Direction.values()) {
                List<BakedQuad> quads = part.getQuads(side);
                any = any == null && !quads.isEmpty() ? quads.getFirst() : any;
            }
        }
        return any;
    }

    private static int tintOf(ClientLevel level, BlockPos pos, BlockState state, BakedQuad quad) {
        int index = quad.materialInfo().tintIndex();
        if (index < 0) {
            return GooRenderUtil.OPAQUE_WHITE;
        }
        @Nullable BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, index);
        return source == null ? GooRenderUtil.OPAQUE_WHITE : ARGB.opaque(source.colorInWorld(state, level, pos));
    }
}
