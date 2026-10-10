package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ber.MeltMesh;
import com.mercuriusxeno.goo.client.ber.MeltMeshGoo;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.network.DrinkPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws every Unmake drink as one surface: the field of its blocks' boxes
 * and its streams' skeletons, built fresh every frame and marched on the GPU
 * by the drink field shader, so there is no mesh, no cell and nothing carried
 * on between ticks; blocks, streams and their joins are one skin with no seam,
 * blending like metaballs where they meet, moving every frame and never
 * standing or vanishing mid-air, and the drink is drawn until its last stream
 * is spent. Each piece of the skin wears its block's own texture from the
 * block through its funnel, laid along the liquid and round the stream so it
 * rides the flow unstretched, and over the standing block where it stands,
 * solid; past the funnel's end it wears the block's goo types alone, roiling
 * in mingled blotches that share the skin by volume, one standing boundary
 * between the two that the liquid flows through.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DrinkRenderer {

    /** The shares of a path its light is read at: the block, just before it, the middle of the way and the hand. */
    private static final double[] LIGHT_SHARES = {0.0, 0.25, 0.5, 1.0};
    /** Where another player's glove hangs before their eyes, in blocks. */
    private static final double GLOVE_AHEAD = 0.5;
    /** How far right of another player's look their glove hangs, in blocks. */
    private static final double GLOVE_RIGHT = 0.35;
    /** How far below another player's eyes their glove hangs, in blocks. */
    private static final double GLOVE_BELOW = 0.45;
    private static final int LIGHT_MASK = 0xFFFF;
    private static final int SKY_SHIFT = 16;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double MILLIS_PER_NANO = 1e-6;
    private static final String UPLOADED = "Unmake drink uploaded in {} ms: {} regions, {} bodies or entries dropped";
    private static final GooRenderUtil.UvRect NO_SPRITE = new GooRenderUtil.UvRect(0, 0, 0, 0);
    /** Each drink's streams as last built, by its drinker then block, which say where liquid still flows. */
    private static final Map<Integer, Map<BlockPos, DrinkTree.Stream>> LAST = new HashMap<>();
    /** The drinkers whose drink still showed liquid last frame. */
    private static final Set<Integer> SHOWING = new HashSet<>();
    /** Each streaming block's skin, by its drinker then block, read once from its model. */
    private static final Map<Integer, Map<BlockPos, Skin>> SKINS = new HashMap<>();
    private static long loggedTick = Long.MIN_VALUE;

    private DrinkRenderer() {
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
     * What a block's skin is drawn with once its liquid flows: its sprite, its tint and its goo types.
     *
     * @param sprite the block's sprite, its top
     * @param tint   the block's tint
     * @param layers the block's goo types mingled over it
     */
    private record Skin(GooRenderUtil.UvRect sprite, int tint, List<DrinkUpload.Layer> layers) {
    }

    /**
     * Draws every drink this frame, after the level, against its depth.
     *
     * @param event the level stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        long began = System.nanoTime();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double ticks = level.getGameTime() + partialTick;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        List<ClientDrinks.Drink> drinks = ClientDrinks.CLIENT.live(ticks, SHOWING::contains);
        forgetAllBut(drinks);
        List<DrinkUpload.Block> blocks = new ArrayList<>();
        for (ClientDrinks.Drink drink : drinks) {
            Entity drinker = level.getEntity(drink.playerId());
            if (drinker != null) {
                Vec3 pull = drink.layout().pullToward(drinker.getViewVector(partialTick), ticks);
                blocks.addAll(uploadsOf(level, drink, new Frame(gloveOf(mc, drinker, partialTick), pull, camera, ticks)));
            }
        }
        DrinkPass.draw(blocks, event.getModelViewMatrix());
        logOnce(level.getGameTime(), began, blocks);
    }

    private static void forgetAllBut(List<ClientDrinks.Drink> drinks) {
        Set<Integer> drinkers = new HashSet<>();
        drinks.forEach(drink -> drinkers.add(drink.playerId()));
        LAST.keySet().retainAll(drinkers);
        SKINS.keySet().retainAll(drinkers);
        SHOWING.retainAll(drinkers);
    }

    private static void logOnce(long tick, long began, List<DrinkUpload.Block> blocks) {
        if (blocks.isEmpty() || tick == loggedTick) {
            return;
        }
        loggedTick = tick;
        int dropped = 0;
        for (DrinkUpload.Block block : blocks) {
            dropped += block.dropped();
        }
        Goo.LOGGER.debug(UPLOADED, Math.round((System.nanoTime() - began) * MILLIS_PER_NANO), blocks.size(), dropped);
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
    static Vec3 gloveOf(Minecraft mc, Entity drinker, float partialTick) {
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
     * One drink's uploads this frame: its tree built for the frame, every
     * stream with liquid still to show uploaded with every other as its
     * neighbour, streams with nothing left to show left out.
     *
     * @param level the client level
     * @param drink the drink
     * @param frame the frame
     * @return the uploads
     */
    private static List<DrinkUpload.Block> uploadsOf(ClientLevel level, ClientDrinks.Drink drink, Frame frame) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        List<DrinkTree.Block> blocks = blocksOf(level, drink, states);
        Map<BlockPos, DrinkTree.Stream> last = LAST.getOrDefault(drink.playerId(), Map.of());
        drink.layout().place(states.keySet(), frame.glove(), frame.ticks(),
                (pos, distance) -> last.containsKey(pos) && last.get(pos).flowingAt(distance));
        List<DrinkField.Skeleton> skeletons = new ArrayList<>();
        List<DrinkUpload.Coat> coats = new ArrayList<>();
        Map<BlockPos, DrinkTree.Stream> built = new HashMap<>();
        for (DrinkTree.Stream stream : DrinkTree.build(blocks, drink.layout(), frame.glove(), frame.pull(),
                frame.ticks())) {
            built.put(stream.block().pos(), stream);
            if (!stream.spent()) {
                skeletons.add(DrinkTree.skeleton(stream));
                coats.add(coatOf(level, drink.playerId(), stream, states.get(stream.block().pos()), frame.ticks()));
            }
        }
        LAST.put(drink.playerId(), built);
        return uploads(drink.playerId(), skeletons, coats, frame.camera());
    }

    private static List<DrinkUpload.Block> uploads(int id, List<DrinkField.Skeleton> skeletons,
                                                   List<DrinkUpload.Coat> coats, Vec3 camera) {
        if (skeletons.isEmpty()) {
            SHOWING.remove(id);
            return List.of();
        }
        SHOWING.add(id);
        return DrinkUpload.of(skeletons, coats, camera, DrinkPass.depthZeroToOne());
    }

    private static List<DrinkTree.Block> blocksOf(ClientLevel level, ClientDrinks.Drink drink,
                                                  Map<BlockPos, BlockState> states) {
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
        return blocks;
    }

    /**
     * The block a streaming block is seen as this frame: the one its melting
     * stand-in holds, the block itself while it still stands as itself with
     * the square on its way, or nothing once it is gone.
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
     * What a stream is drawn with: its block's skin, in the light along its path.
     *
     * @param level  the client level
     * @param id     the drinker
     * @param stream the stream
     * @param state  its block
     * @param now    the game time, with the partial tick
     * @return the coat
     */
    private static DrinkUpload.Coat coatOf(ClientLevel level, int id, DrinkTree.Stream stream, BlockState state,
                                           double now) {
        int light = lightAlong(level, stream, now);
        Skin skin = SKINS.computeIfAbsent(id, ignored -> new HashMap<>())
                .computeIfAbsent(stream.block().pos(), pos -> skinOf(level, pos, state));
        return new DrinkUpload.Coat(skin.tint(), skin.sprite(), light & LIGHT_MASK, light >>> SKY_SHIFT,
                skin.layers());
    }

    /**
     * @param level the client level
     * @param pos   the block
     * @param state the block
     * @return its skin: its top's sprite and tint, and its goo types as layers, largest first
     */
    private static Skin skinOf(ClientLevel level, BlockPos pos, BlockState state) {
        BakedQuad quad = faceQuad(level, pos, state);
        GooRenderUtil.UvRect sprite = quad == null ? NO_SPRITE : MeltMesh.spriteOf(quad);
        int tint = quad == null ? GooRenderUtil.OPAQUE_WHITE : MeltMesh.tintOf(state, level, pos, quad);
        return new Skin(sprite, tint, layersOf(MeltMeshGoo.of(state)));
    }

    /**
     * @param goo a block's goo types
     * @return each type as a layer, with the volume ratio of the types before it and through it
     */
    static List<DrinkUpload.Layer> layersOf(MingledGoo goo) {
        List<DrinkUpload.Layer> layers = new ArrayList<>();
        for (int index = 0; index < Math.min(DrinkUpload.MOST_LAYERS, goo.types().size()); index++) {
            ResourceKey<GooTypeDefinition> type = goo.types().get(index);
            float before = index == 0 ? 0f : goo.cumulative().get(index - 1);
            layers.add(new DrinkUpload.Layer(GooSubmitter.fluidTint(type),
                    GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(type)), before,
                    goo.cumulative().get(index), Math.max(0, GooTypes.indexOf(type))));
        }
        return layers;
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
