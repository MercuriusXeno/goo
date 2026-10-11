package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ber.MeltMeshGoo;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.network.DrinkPayload;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
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
 * is spent. No texture anywhere: each piece of the skin wears one solid
 * color, the block's particle sprite averaged over its raw pixels at the
 * block crossfading by its share of the route to the goo's own color, its
 * types' tints blended by volume, at the hand, a blend the whole way with no
 * band and no boundary and nothing to stretch. The drink is drawn after the level
 * against its depth, but the player's own stretch by the glove is drawn in
 * the hand's pass, so it shows over the hand and ends in the palm rather than
 * disappearing behind the knuckles.
 * decision unmake-waves-dissolve-by-crucible-cost
 * decision unmake-skin-is-a-solid-sampled-color
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
    /** Each drink's streams as last built, by its drinker then block, which say where liquid still flows. */
    private static final Map<Integer, Map<BlockPos, DrinkTree.Stream>> LAST = new HashMap<>();
    /** The drinkers whose drink still showed liquid last frame. */
    private static final Set<Integer> SHOWING = new HashSet<>();
    /** Each streaming block's skin, by its drinker then block, read once from its model. */
    private static final Map<Integer, Map<BlockPos, Skin>> SKINS = new HashMap<>();
    /** Blocks from the local glove within which a region of the player's own drink is drawn in the hand's pass. */
    static final double HAND_REACH = 1.5;
    private static long loggedTick = Long.MIN_VALUE;
    /** The regions by the local glove this frame, waiting for the hand's pass. */
    private static List<DrinkUpload.Block> inHand = List.of();
    /** The level's view matrix this frame, which the hand's pass bobs as the hand is. */
    private static @Nullable Matrix4f levelView;
    /** Whether the hand's pass drew since the level's last, so the next frame may leave it the stretch by the glove. */
    private static boolean handDrew;

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
     * What a block's skin is drawn with once its liquid flows: two solid colors.
     *
     * @param blockColor the block's color, worn at the block
     * @param gooColor   its goo's color, worn at the hand
     */
    private record Skin(int blockColor, int gooColor) {
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
        Passes passes = passesOf(mc, level, drinks, camera, ticks);
        DrinkPass.draw(passes.level, event.getModelViewMatrix());
        inHand = passes.hand;
        levelView = new Matrix4f(event.getModelViewMatrix());
        handDrew = false;
        logOnce(level.getGameTime(), began, passes);
    }

    private static Passes passesOf(Minecraft mc, ClientLevel level, List<ClientDrinks.Drink> drinks, Vec3 camera,
                                   double ticks) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Passes passes = new Passes();
        for (ClientDrinks.Drink drink : drinks) {
            Entity drinker = level.getEntity(drink.playerId());
            if (drinker != null) {
                Vec3 pull = drink.layout().pullToward(drinker.getViewVector(partialTick), ticks);
                Frame frame = new Frame(gloveOf(mc, drinker, partialTick), pull, camera, ticks);
                passes.add(uploadsOf(level, drink, frame), inHand(mc, drinker) ? frame.glove().subtract(camera) : null);
            }
        }
        return passes;
    }

    /**
     * Whether a drinker's stretch of drink by the glove is drawn in the hand's
     * pass: the local player's, seen in first person, while the hand is being
     * drawn at all; with the hand hidden the whole drink stays in the level's.
     *
     * @param mc      the client
     * @param drinker the drinker
     * @return whether to draw the stretch over the hand
     */
    private static boolean inHand(Minecraft mc, Entity drinker) {
        return drinker == mc.player && mc.options.getCameraType().isFirstPerson() && handDrew;
    }

    /**
     * Draws the local drink's stretch by the glove in the hand's pass, where
     * the level's depth is cleared and the hand is yet to be drawn, so the
     * stream's last stretch shows over the hand and the hand drawn after it is
     * hidden only where the stream is in front; drawn in the hand's own
     * projection with the view bobbed as the hand is, so it lines up with the
     * glove.
     *
     * @param event the hand event, the main hand's starting the pass
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        handDrew = true;
        if (inHand.isEmpty() || levelView == null) {
            return;
        }
        Matrix4f bob = new Matrix4f(levelView).mul(event.getPoseStack().last().pose());
        DrinkPass.draw(inHand, bob.mul(levelView));
        inHand = List.of();
    }

    private static void forgetAllBut(List<ClientDrinks.Drink> drinks) {
        Set<Integer> drinkers = new HashSet<>();
        drinks.forEach(drink -> drinkers.add(drink.playerId()));
        LAST.keySet().retainAll(drinkers);
        SKINS.keySet().retainAll(drinkers);
        SHOWING.retainAll(drinkers);
    }

    private static void logOnce(long tick, long began, Passes passes) {
        if (passes.regions() == 0 || tick == loggedTick) {
            return;
        }
        loggedTick = tick;
        Goo.LOGGER.debug(UPLOADED, Math.round((System.nanoTime() - began) * MILLIS_PER_NANO), passes.regions(),
                passes.dropped());
    }

    /**
     * This frame's uploads by the pass that draws them: the level's for the
     * drink at large, the hand's for the regions within {@link #HAND_REACH}
     * of the local glove.
     */
    private static final class Passes {

        private final List<DrinkUpload.Block> level = new ArrayList<>();
        private final List<DrinkUpload.Block> hand = new ArrayList<>();

        /**
         * @param uploads a drink's uploads
         * @param glove   the drinker's glove, camera-relative, or null to draw the whole drink in the level's pass
         */
        void add(List<DrinkUpload.Block> uploads, @Nullable Vec3 glove) {
            for (DrinkUpload.Block block : uploads) {
                (glove != null && block.within(glove, HAND_REACH) ? hand : level).add(block);
            }
        }

        int regions() {
            return level.size() + hand.size();
        }

        int dropped() {
            int dropped = 0;
            for (DrinkUpload.Block block : level) {
                dropped += block.dropped();
            }
            for (DrinkUpload.Block block : hand) {
                dropped += block.dropped();
            }
            return dropped;
        }
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
        drink.layout().place(states.keySet(), frame.glove(), frame.ticks(), DrinkTree.flowingOf(last, states.keySet()));
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
     * What a stream is drawn with: its block's two colors, in the light along its path.
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
        return new DrinkUpload.Coat(skin.blockColor(), skin.gooColor(), light & LIGHT_MASK, light >>> SKY_SHIFT);
    }

    /**
     * @param level the client level
     * @param pos   the block
     * @param state the block
     * @return its skin: its particle sprite's raw mean, and its goo types' tints blended by volume share, or the
     *         block's own color where it holds no goo
     */
    private static Skin skinOf(ClientLevel level, BlockPos pos, BlockState state) {
        TextureAtlasSprite particle = Minecraft.getInstance().getModelManager().getBlockStateModelSet()
                .getParticleMaterial(state, level, pos).sprite();
        int blockColor = DrinkColors.blockColorOf(pixelsOf(particle.contents().getOriginalImage()));
        MingledGoo goo = MeltMeshGoo.of(state);
        return new Skin(blockColor, goo.types().isEmpty() ? blockColor : gooColorOf(goo));
    }

    /**
     * @param image a sprite's raw image
     * @return its pixels, ARGB, row by row
     */
    private static int[] pixelsOf(NativeImage image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                pixels[y * image.getWidth() + x] = image.getPixel(x, y);
            }
        }
        return pixels;
    }

    /**
     * @param goo a block's goo types
     * @return their tints blended by their shares of the volume
     */
    static int gooColorOf(MingledGoo goo) {
        int[] tints = new int[goo.types().size()];
        float[] shares = new float[tints.length];
        for (int index = 0; index < tints.length; index++) {
            tints[index] = GooSubmitter.fluidTint(goo.types().get(index));
            shares[index] = goo.share(index);
        }
        return DrinkColors.gooColorOf(tints, shares);
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
}
