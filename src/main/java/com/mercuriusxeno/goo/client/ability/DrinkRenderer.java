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
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Draws every Unmake drink as one surface: the field of its blocks' boxes
 * and its streams' skeletons meshed each frame, so blocks, streams and their
 * joins are one skin with no seam, blending like metaballs where they meet.
 * Each piece of the skin wears its block's own texture laid over the world
 * at its own size and slid with the flow, solid, with the block's goo types
 * roiling over it through the vats' mingle shader in blotches that cover more
 * of it along the block's route but never all of it, the types sharing the
 * blotches by volume.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DrinkRenderer {

    /** How much further out each goo layer's skin stands than the one under it, so they never fight. */
    static final double LAYER_STEP = 0.008;
    /** The share of a stream's skin the goo covers by the hand, so the block's texture shows mingled the whole way. */
    static final float GOO_REACH = 0.45f;
    /** The most goo types layered over one stream; past three the blotches read as noise. */
    private static final int MAX_LAYERS = 3;
    /** The shares of a path its light is read at: the block, just before it, the middle of the way and the hand. */
    private static final double[] LIGHT_SHARES = {0.0, 0.25, 0.5, 1.0};
    /** Where another player's glove hangs before their eyes, in blocks. */
    private static final double GLOVE_AHEAD = 0.5;
    /** How far right of another player's look their glove hangs, in blocks. */
    private static final double GLOVE_RIGHT = 0.35;
    /** How far below another player's eyes their glove hangs, in blocks. */
    private static final double GLOVE_BELOW = 0.45;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    /** Each drink's surface as last meshed, by its drinker. */
    private static final Map<Integer, Surface> SURFACES = new HashMap<>();
    /** Each drink's mesh in flight on a background thread, by its drinker. */
    private static final Map<Integer, CompletableFuture<Surface>> MESHING = new HashMap<>();

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
        List<ClientDrinks.Drink> drinks = ClientDrinks.CLIENT.live(ticks);
        List<Integer> drinkers = drinks.stream().map(ClientDrinks.Drink::playerId).toList();
        SURFACES.keySet().retainAll(drinkers);
        MESHING.keySet().retainAll(drinkers);
        for (ClientDrinks.Drink drink : drinks) {
            Entity drinker = level.getEntity(drink.playerId());
            if (drinker != null) {
                Vec3 pull = drink.layout().pullToward(drinker.getViewVector(partialTick), ticks);
                submitDrink(event, level, drink, new Frame(gloveOf(mc, drinker, partialTick), pull, camera, ticks));
            }
        }
    }

    /**
     * What a frame draws a drink against.
     *
     * @param glove  the drinker's glove, in the world
     * @param pull   the unit direction the trunk flows as it enters the hand
     * @param camera the camera's world position
     * @param ticks  the game time including the partial tick
     */
    private record Frame(Vec3 glove, Vec3 pull, Vec3 camera, double ticks) {
    }

    /**
     * What one block's piece of the skin is drawn with.
     *
     * @param state the block
     * @param goo   the goo it becomes
     * @param tint  the block's tint
     * @param sprite the block's sprite, its top
     * @param light the light along its stream
     */
    private record Coat(BlockState state, MingledGoo goo, int tint, GooRenderUtil.UvRect sprite, int light) {
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

    /**
     * Submits one drink: its blocks' tree of streams meshed as one surface.
     *
     * @param event the custom geometry submit event
     * @param level the client level
     * @param drink the drink
     * @param frame the frame
     */
    private static void submitDrink(SubmitCustomGeometryEvent event, ClientLevel level, ClientDrinks.Drink drink,
                                    Frame frame) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        List<DrinkTree.Block> blocks = new ArrayList<>();
        for (DrinkPayload.Streaming streaming : drink.streaming()) {
            BlockState seen = level.getBlockEntity(streaming.pos()) instanceof MeltingBlockEntity melting
                    ? melting.original() : null;
            BlockState state = ClientDrinks.CLIENT.blockOf(streaming.pos(), seen);
            if (state != null) {
                states.put(streaming.pos(), state);
                blocks.add(new DrinkTree.Block(streaming.pos(), Vec3.atCenterOf(streaming.pos()),
                        DrinkTree.scaleOf(MeltMeshGoo.volumeOf(state)), streaming.start(), streaming.end()));
            }
        }
        drink.layout().place(states.keySet(), frame.glove());
        Surface surface = surfaceOf(level, drink, blocks, states, frame);
        if (surface != null) {
            submitSurface(event, surface, frame);
        }
    }

    /**
     * A drink's surface as last meshed: once a tick, on a background thread,
     * since meshing is the cost; between ticks the hand's end of it is
     * carried with the glove.
     *
     * @param tick  the tick it was meshed for
     * @param glove where the glove was then
     * @param quads the surface
     * @param coats what each stream's piece of the skin is drawn with
     */
    private record Surface(long tick, Vec3 glove, List<DrinkMesher.Quad> quads, Map<DrinkTree.Stream, Coat> coats) {
    }

    /**
     * The surface to draw a drink with this frame: the last one meshed, a
     * finished background mesh taking its place, and a new mesh started on
     * this tick's skeleton where none is in flight.
     *
     * @param level  the client level
     * @param drink  the drink
     * @param blocks its blocks
     * @param states each block's state
     * @param frame  the frame
     * @return the surface, or null before the first mesh lands
     */
    private static @Nullable Surface surfaceOf(ClientLevel level, ClientDrinks.Drink drink,
                                               List<DrinkTree.Block> blocks, Map<BlockPos, BlockState> states,
                                               Frame frame) {
        int id = drink.playerId();
        landMesh(id);
        Surface shown = SURFACES.get(id);
        if (!MESHING.containsKey(id) && (shown == null || shown.tick() != level.getGameTime())) {
            MESHING.put(id, meshAsync(level, drink, blocks, states, frame));
        }
        return shown;
    }

    /**
     * Takes a drink's finished background mesh as its surface to draw.
     *
     * @param id the drinker
     */
    private static void landMesh(int id) {
        CompletableFuture<Surface> meshing = MESHING.get(id);
        if (meshing == null || !meshing.isDone()) {
            return;
        }
        MESHING.remove(id);
        if (!meshing.isCompletedExceptionally()) {
            SURFACES.put(id, meshing.join());
        }
    }

    private static CompletableFuture<Surface> meshAsync(ClientLevel level, ClientDrinks.Drink drink,
                                                        List<DrinkTree.Block> blocks, Map<BlockPos, BlockState> states,
                                                        Frame frame) {
        List<DrinkField.Skeleton> skeletons = new ArrayList<>();
        Map<DrinkTree.Stream, Coat> coats = new HashMap<>();
        for (DrinkTree.Stream stream : DrinkTree.build(blocks, drink.layout(), frame.glove(), frame.pull(),
                frame.ticks())) {
            skeletons.add(DrinkTree.skeleton(stream, frame.ticks()));
            coats.put(stream, coatOf(level, stream, states.get(stream.block().pos()), frame.ticks()));
        }
        long tick = level.getGameTime();
        Vec3 glove = frame.glove();
        return CompletableFuture.supplyAsync(() -> new Surface(tick, glove, DrinkMesher.mesh(skeletons), coats),
                Util.backgroundExecutor());
    }

    private static Coat coatOf(ClientLevel level, DrinkTree.Stream stream, BlockState state, double now) {
        BakedQuad quad = faceQuad(level, stream.block().pos(), state);
        GooRenderUtil.UvRect sprite = quad == null ? new GooRenderUtil.UvRect(0, 0, 0, 0) : MeltMesh.spriteOf(quad);
        int tint = quad == null ? GooRenderUtil.OPAQUE_WHITE : MeltMesh.tintOf(state, level, stream.block().pos(), quad);
        return new Coat(state, MeltMeshGoo.of(state), tint, sprite, lightAlong(level, stream, now));
    }

    /**
     * The light a stream is drawn in: the brightest along its path, so a block
     * deep in a wall is lit as the air its stream runs through, not as the
     * dark inside the wall.
     *
     * @param level  the client level
     * @param stream the stream
     * @param now    the game time, with the partial tick
     * @return the packed light
     */
    private static int lightAlong(ClientLevel level, DrinkTree.Stream stream, double now) {
        int brightest = 0;
        for (double share : LIGHT_SHARES) {
            brightest = LightCoordsUtil.max(brightest, LevelRenderer.getLightCoords(level,
                    BlockPos.containing(DrinkStream.pointAt(stream.path(), share, now))));
        }
        return brightest;
    }

    /**
     * Submits the drink's surface: every quad in its block's texture, solid,
     * then each goo type's blotches over it.
     *
     * @param event   the custom geometry submit event
     * @param surface the surface as last meshed
     * @param frame   the frame
     */
    private static void submitSurface(SubmitCustomGeometryEvent event, Surface surface, Frame frame) {
        List<DrinkMesher.Quad> quads = surface.quads();
        Map<DrinkTree.Stream, Coat> coats = surface.coats();
        if (quads.isEmpty()) {
            return;
        }
        Vec3 carried = frame.glove().subtract(surface.glove());
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooSubmitter.solidOnBlockAtlas(),
                (pose, consumer) -> {
                    for (DrinkMesher.Quad quad : quads) {
                        Coat coat = coatOf(coats, quad);
                        emitQuad(new RenderContext(pose, consumer, coat.light()), quad, coat.sprite(), coat.tint(), 0,
                                frame, carried);
                    }
                });
        for (int layer = 0; layer < MAX_LAYERS; layer++) {
            submitGooLayer(event, quads, coats, layer, frame, carried);
        }
    }

    private static Coat coatOf(Map<DrinkTree.Stream, Coat> coats, DrinkMesher.Quad quad) {
        return coats.get(quad.vertices()[0].skeleton().stream());
    }

    /**
     * Submits one goo layer over the surface on the vats' mingle shader: each
     * quad whose block has that many goo types, in that type's sprite, in
     * roiling blotches whose share of the skin grows along the block's route.
     *
     * @param event   the custom geometry submit event
     * @param quads   the surface
     * @param coats   what each stream's piece of the skin is drawn with
     * @param layer   the goo type's index, largest first
     * @param frame   the frame
     * @param carried how far the glove has moved since the surface was meshed
     */
    private static void submitGooLayer(SubmitCustomGeometryEvent event, List<DrinkMesher.Quad> quads,
                                       Map<DrinkTree.Stream, Coat> coats, int layer, Frame frame, Vec3 carried) {
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooRenderTypes.gooFluidSurface(
                Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location()),
                (pose, consumer) -> {
                    for (DrinkMesher.Quad quad : quads) {
                        MingledGoo goo = coatOf(coats, quad).goo();
                        if (layer < goo.types().size()) {
                            emitGooQuad(pose, consumer, quad, goo, layer, frame, carried);
                        }
                    }
                });
    }

    private static void emitGooQuad(PoseStack.Pose pose, VertexConsumer consumer, DrinkMesher.Quad quad,
                                    MingledGoo goo, int layer, Frame frame, Vec3 carried) {
        float reach = 0f;
        for (DrinkMesher.Vertex vertex : quad.vertices()) {
            reach += (float) vertex.ring().share() / quad.vertices().length;
        }
        int tint = GooSubmitter.fluidTint(goo.types().get(layer));
        RenderContext ctx = RenderContext.banded(pose, consumer, tint, bandOf(goo, layer, reach * GOO_REACH));
        emitQuad(ctx, quad, GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(goo.types().get(layer))), tint,
                LAYER_STEP * (layer + 1), frame, carried);
    }

    /**
     * The band a goo type's layer draws with where the goo covers a share of
     * the skin: the layers stack over the block's texture so that each type
     * covers its volume ratio of that share.
     *
     * @param goo   the goo
     * @param layer the type's index, largest first
     * @param reach the share of the skin the goo covers there, 0 to 1
     * @return the band
     */
    static TypeBand bandOf(MingledGoo goo, int layer, float reach) {
        float before = layer == 0 ? 0f : goo.cumulative().get(layer - 1);
        float lo = 1f - reach * (1f - before);
        float hi = 1f - reach * (1f - goo.cumulative().get(layer));
        return new TypeBand(goo.types().get(layer), lo, hi, layer);
    }

    /**
     * Emits one quad of the surface: each vertex carried with the glove by
     * how near the hand it is, lifted off the skin, its texture laid over the
     * world at its own size on the two axes square to its normal and slid
     * against the flow, so it rides the liquid unstretched.
     *
     * @param ctx     the render context
     * @param quad    the quad
     * @param sprite  the sprite
     * @param color   the colour
     * @param lift    how far off the skin the quad stands
     * @param frame   the frame
     * @param carried how far the glove has moved since the surface was meshed
     */
    private static void emitQuad(RenderContext ctx, DrinkMesher.Quad quad, GooRenderUtil.UvRect sprite, int color,
                                 double lift, Frame frame, Vec3 carried) {
        for (DrinkMesher.Vertex vertex : quad.vertices()) {
            double share = vertex.ring().share();
            Vec3 at = vertex.point().add(carried.scale(share * share * share));
            Vec3 point = at.add(vertex.normal().scale(lift)).subtract(frame.camera());
            Vec3 slid = vertex.point().subtract(vertex.ring().flow().scale(frame.ticks() * DrinkStream.FLOW));
            Vec3 normal = vertex.normal();
            Vec3 uv = textureAxes(normal, slid);
            ctx.vertexColored(color, (float) point.x, (float) point.y, (float) point.z,
                    sprite.u0() + (sprite.u1() - sprite.u0()) * DrinkStream.textureAt(uv.x),
                    sprite.v0() + (sprite.v1() - sprite.v0()) * DrinkStream.textureAt(uv.y),
                    (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    /**
     * @param normal the surface's normal at a point
     * @param slid   the point, slid with the flow
     * @return the point's two coordinates on the world axes square to the axis its normal most faces, as x and y
     */
    static Vec3 textureAxes(Vec3 normal, Vec3 slid) {
        double ax = Math.abs(normal.x);
        double ay = Math.abs(normal.y);
        double az = Math.abs(normal.z);
        if (ay >= ax && ay >= az) {
            return new Vec3(slid.x, slid.z, 0);
        }
        return ax >= az ? new Vec3(slid.z, slid.y, 0) : new Vec3(slid.x, slid.y, 0);
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
        List<BakedQuad> quads = MeltMesh.quadsOf(state, level, pos);
        for (BakedQuad quad : quads) {
            if (quad.direction() == Direction.UP) {
                return quad;
            }
        }
        return quads.isEmpty() ? null : quads.getFirst();
    }
}
