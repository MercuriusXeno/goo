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
import com.mercuriusxeno.goo.type.GooTypes;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Draws every Unmake drink as one surface: the field of its blocks' boxes
 * and its streams' skeletons meshed once a tick off the render thread, the
 * skin carried on at the pace it was moving between meshes and the drink
 * drawn until its skin meshes empty, so blocks, streams and their
 * joins are one skin with no seam, blending like metaballs where they meet,
 * moving every frame and never standing or vanishing mid-air.
 * Each piece of the skin wears its block's own texture, laid along the liquid
 * and round the stream so it rides the flow unstretched, and over the standing
 * block where it stands, solid, with the block's goo types roiling over it
 * through the vats' mingle shader in blotches that cover more of it along the
 * block's route but never all of it, the types sharing the blotches by
 * volume; a block's zoop wears unstable goo's own sprite.
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
    /** The most ticks a skin is carried on past its mesh at the pace it was moving, before it holds still. */
    static final double EXTRAPOLATE = 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double MILLIS_PER_NANO = 1e-6;
    private static final String MESHED = "Unmake drink meshed in {} ms: cell 1/{}, {} quads, {} streams";
    /** Each drink's surface as last meshed, by its drinker. */
    private static final Map<Integer, Surface> SURFACES = new HashMap<>();
    /** Each drink's mesh in flight on a background thread, by its drinker. */
    private static final Map<Integer, CompletableFuture<Surface>> MESHING = new HashMap<>();
    /** Each drink's streams as last built, by its drinker then block, which say where liquid still flows. */
    private static final Map<Integer, Map<BlockPos, DrinkTree.Stream>> LAST = new HashMap<>();

    private DrinkRenderer() {
    }

    /**
     * Submits every drink this frame: a drink is drawn until its skin meshes
     * empty, so nothing still in the air is cut.
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
        List<ClientDrinks.Drink> drinks = ClientDrinks.CLIENT.live(ticks, DrinkRenderer::stillSeen);
        Set<Integer> drinkers = new HashSet<>();
        drinks.forEach(drink -> drinkers.add(drink.playerId()));
        MESHING.keySet().retainAll(drinkers);
        LAST.keySet().retainAll(drinkers);
        SURFACES.keySet().retainAll(drinkers);
        for (ClientDrinks.Drink drink : drinks) {
            Entity drinker = level.getEntity(drink.playerId());
            if (drinker != null) {
                Vec3 pull = drink.layout().pullToward(drinker.getViewVector(partialTick), ticks);
                submitDrink(event, level, drink, new Frame(gloveOf(mc, drinker, partialTick), pull, camera, ticks));
            }
        }
    }

    /**
     * @param id a drinker's entity id
     * @return whether their drink's skin still shows anything, or a mesh of it is still in flight
     */
    private static boolean stillSeen(int id) {
        Surface surface = SURFACES.get(id);
        return MESHING.containsKey(id) || surface != null && !surface.quads().isEmpty();
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
     * How a skin has moved on since it was meshed.
     *
     * @param carried how far the glove has moved since
     * @param since   the ticks since, with the partial tick
     */
    record Motion(Vec3 carried, double since) {
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
            BlockState state = ClientDrinks.CLIENT.blockOf(streaming.pos(), seenAt(level, streaming.pos()));
            if (state != null) {
                states.put(streaming.pos(), state);
                blocks.add(new DrinkTree.Block(streaming.pos(), Vec3.atCenterOf(streaming.pos()),
                        DrinkTree.scaleOf(MeltMeshGoo.volumeOf(state)), streaming.picked(), streaming.start(),
                        streaming.end()));
            }
        }
        Map<BlockPos, DrinkTree.Stream> last = LAST.getOrDefault(drink.playerId(), Map.of());
        drink.layout().place(states.keySet(), frame.glove(), frame.ticks(),
                (pos, distance) -> last.containsKey(pos) && last.get(pos).flowingAt(distance));
        Surface surface = surfaceOf(level, drink, blocks, states, frame);
        if (surface != null) {
            submitSurface(event, surface, frame);
        }
    }

    /**
     * The block a streaming block is seen as this frame: the one its melting
     * stand-in holds, the block itself while it still stands as itself with
     * the zoop on its way, or nothing once it is gone.
     *
     * @param level the client level
     * @param pos   the block
     * @return the block, or null
     */
    private static @Nullable BlockState seenAt(ClientLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof MeltingBlockEntity melting) {
            return melting.original();
        }
        BlockState state = level.getBlockState(pos);
        return state.isAir() ? null : state;
    }

    /**
     * A drink's surface as last meshed: once a tick, on a background thread,
     * since meshing is the cost; between meshes the skin is carried on at the
     * pace it was moving and the hand's end of it with the glove.
     *
     * @param tick   the tick it was meshed for
     * @param at     the game time it was meshed for, with the partial tick
     * @param glove  where the glove was then
     * @param quads  the surface
     * @param coats  what each stream's piece of the skin is drawn with
     * @param millis how long the mesh took
     */
    private record Surface(long tick, double at, Vec3 glove, List<DrinkMesher.Quad> quads,
                           Map<DrinkTree.Stream, Coat> coats, double millis) {
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
            Surface surface = meshing.join();
            SURFACES.put(id, surface);
            Goo.LOGGER.debug(MESHED, Math.round(surface.millis()), Math.round(1 / DrinkMesher.CELL),
                    surface.quads().size(), surface.coats().size());
        }
    }

    /**
     * Starts a drink's mesh on a background thread: its tree built for this
     * frame and for a tick ahead, every stream with liquid still to show
     * skeletoned, streams with nothing left to show left out.
     *
     * @param level  the client level
     * @param drink  the drink
     * @param blocks its blocks
     * @param states each block's state
     * @param frame  the frame
     * @return the mesh in flight
     */
    private static CompletableFuture<Surface> meshAsync(ClientLevel level, ClientDrinks.Drink drink,
                                                        List<DrinkTree.Block> blocks, Map<BlockPos, BlockState> states,
                                                        Frame frame) {
        List<DrinkField.Skeleton> skeletons = new ArrayList<>();
        Map<DrinkTree.Stream, Coat> coats = new HashMap<>();
        Map<BlockPos, DrinkTree.Stream> built = new HashMap<>();
        for (DrinkTree.Stream stream : DrinkTree.build(blocks, drink.layout(), frame.glove(), frame.pull(),
                frame.ticks())) {
            built.put(stream.block().pos(), stream);
            if (!stream.spent()) {
                skeletons.add(DrinkTree.skeleton(stream));
                coats.put(stream, coatOf(level, stream, states.get(stream.block().pos()), frame.ticks()));
            }
        }
        LAST.put(drink.playerId(), built);
        List<DrinkField.Skeleton> ahead = aheadOf(drink, blocks, frame, coats.keySet());
        long tick = level.getGameTime();
        return CompletableFuture.supplyAsync(() -> {
            long began = System.nanoTime();
            List<DrinkMesher.Quad> quads = DrinkMesher.mesh(skeletons, ahead, DrinkMesher.CELL);
            return new Surface(tick, frame.ticks(), frame.glove(), quads, coats,
                    (System.nanoTime() - began) * MILLIS_PER_NANO);
        }, Util.backgroundExecutor());
    }

    /**
     * The drink's skeletons a tick ahead, for the streams skeletoned now, in the same order.
     *
     * @param drink  the drink
     * @param blocks its blocks
     * @param frame  the frame
     * @param shown  the streams skeletoned now
     * @return the skeletons a tick ahead
     */
    private static List<DrinkField.Skeleton> aheadOf(ClientDrinks.Drink drink, List<DrinkTree.Block> blocks,
                                                     Frame frame, Set<DrinkTree.Stream> shown) {
        Set<BlockPos> kept = new HashSet<>();
        shown.forEach(stream -> kept.add(stream.block().pos()));
        List<DrinkField.Skeleton> ahead = new ArrayList<>();
        for (DrinkTree.Stream stream : DrinkTree.build(blocks, drink.layout(), frame.glove(), frame.pull(),
                frame.ticks() + 1)) {
            if (kept.contains(stream.block().pos())) {
                ahead.add(DrinkTree.skeleton(stream));
            }
        }
        return ahead;
    }

    /**
     * What a stream's piece of the skin is drawn with: unstable goo's own
     * sprite and no blotches while its zoop flies, then its block's texture
     * with the block's goo roiling over it.
     *
     * @param level  the client level
     * @param stream the stream
     * @param state  its block
     * @param now    the game time, with the partial tick
     * @return the coat
     */
    private static Coat coatOf(ClientLevel level, DrinkTree.Stream stream, BlockState state, double now) {
        if (stream.zooping()) {
            return new Coat(state, MingledGoo.NONE, GooSubmitter.fluidTint(GooTypes.UNSTABLE),
                    GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(GooTypes.UNSTABLE)),
                    lightAlong(level, stream, now));
        }
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
        Motion motion = new Motion(frame.glove().subtract(surface.glove()), frame.ticks() - surface.at());
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooSubmitter.solidOnBlockAtlas(),
                (pose, consumer) -> {
                    for (DrinkMesher.Quad quad : quads) {
                        Coat coat = coatOf(coats, quad);
                        emitQuad(new RenderContext(pose, consumer, coat.light()), quad, coat.sprite(), coat.tint(), 0,
                                frame, motion);
                    }
                });
        for (int layer = 0; layer < MAX_LAYERS; layer++) {
            submitGooLayer(event, quads, coats, layer, frame, motion);
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
     * @param motion  how the skin has moved on since it was meshed
     */
    private static void submitGooLayer(SubmitCustomGeometryEvent event, List<DrinkMesher.Quad> quads,
                                       Map<DrinkTree.Stream, Coat> coats, int layer, Frame frame, Motion motion) {
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(), GooRenderTypes.gooFluidSurface(
                Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location()),
                (pose, consumer) -> {
                    for (DrinkMesher.Quad quad : quads) {
                        MingledGoo goo = coatOf(coats, quad).goo();
                        if (layer < goo.types().size()) {
                            emitGooQuad(pose, consumer, quad, goo, layer, frame, motion);
                        }
                    }
                });
    }

    private static void emitGooQuad(PoseStack.Pose pose, VertexConsumer consumer, DrinkMesher.Quad quad,
                                    MingledGoo goo, int layer, Frame frame, Motion motion) {
        float reach = 0f;
        for (DrinkMesher.Vertex vertex : quad.vertices()) {
            reach += (float) vertex.ring().share() / quad.vertices().length;
        }
        int tint = GooSubmitter.fluidTint(goo.types().get(layer));
        RenderContext ctx = RenderContext.banded(pose, consumer, tint, bandOf(goo, layer, reach * GOO_REACH));
        emitQuad(ctx, quad, GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(goo.types().get(layer))), tint,
                LAYER_STEP * (layer + 1), frame, motion);
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
     * Emits one quad of the surface: each vertex carried on along its normal
     * at the pace the skin was moving there for the time since the mesh, and
     * with the glove by how near the hand it is; lifted off the skin; its
     * texture read at the place the mesher gave it in the quad's one frame,
     * mirrored so it tiles and wraps with no seam.
     *
     * @param ctx    the render context
     * @param quad   the quad
     * @param sprite the sprite
     * @param color  the colour
     * @param lift   how far off the skin the quad stands
     * @param frame  the frame
     * @param motion how the skin has moved on since it was meshed
     */
    private static void emitQuad(RenderContext ctx, DrinkMesher.Quad quad, GooRenderUtil.UvRect sprite, int color,
                                 double lift, Frame frame, Motion motion) {
        for (int corner = 0; corner < quad.vertices().length; corner++) {
            DrinkMesher.Vertex vertex = quad.vertices()[corner];
            Vec3 at = carriedOn(vertex, motion);
            Vec3 point = at.add(vertex.normal().scale(lift)).subtract(frame.camera());
            Vec3 normal = vertex.normal();
            ctx.vertexColored(color, (float) point.x, (float) point.y, (float) point.z,
                    sprite.u0() + (sprite.u1() - sprite.u0()) * DrinkStream.textureAt(quad.along()[corner]),
                    sprite.v0() + (sprite.v1() - sprite.v0()) * DrinkStream.textureAt(quad.around()[corner]),
                    (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    /**
     * @param vertex a vertex of the skin
     * @param motion how the skin has moved on since it was meshed
     * @return where the vertex is now: along its normal at the pace the skin was moving there, for the time since
     *         the mesh up to {@link #EXTRAPOLATE}, and with the glove by the cube of its nearness to the hand
     */
    static Vec3 carriedOn(DrinkMesher.Vertex vertex, Motion motion) {
        double share = vertex.ring().share();
        double since = Math.min(motion.since(), EXTRAPOLATE);
        return vertex.point().add(vertex.normal().scale(vertex.velocity() * since))
                .add(motion.carried().scale(share * share * share));
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
