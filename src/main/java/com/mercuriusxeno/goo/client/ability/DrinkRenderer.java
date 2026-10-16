package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.client.ber.MeltMeshGoo;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds every Unmake drink's tree each frame: its blocks as the client sees
 * them, the layout walk placed against the drinker's glove and aim, and the
 * streams that walk gives, kept so the next frame knows where liquid still
 * flows and the drink stays live until its last stream is spent. Nothing is
 * drawn yet; the drink's streams carry no picture of their own.
 * decision unmake-waves-dissolve-by-crucible-cost
 * decision diagnose-then-fix-unmake-seam-and-ribbing
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
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double HALF = 0.5;
    /** Each drink's streams as last built, by its drinker then block, which say where liquid still flows. */
    private static final Map<Integer, Map<BlockPos, DrinkTree.Stream>> LAST = new HashMap<>();
    /** The drinkers whose drink still showed liquid last frame. */
    private static final Set<Integer> SHOWING = new HashSet<>();

    private DrinkRenderer() {
    }

    /**
     * What a frame builds a drink against.
     *
     * @param glove the drinker's glove, in the world
     * @param pull  the unit direction the trunk flows as it enters the hand
     * @param aim   the drinker's aim line, which picks the spine
     * @param ticks the game time including the partial tick
     */
    private record Frame(Vec3 glove, Vec3 pull, DrinkLayout.Aim aim, double ticks) {
    }

    /**
     * Builds every drink's tree this frame, after the level.
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
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double ticks = level.getGameTime() + partialTick;
        List<ClientDrinks.Drink> drinks = ClientDrinks.CLIENT.live(ticks, SHOWING::contains);
        forgetAllBut(drinks);
        CameraRenderState sight = event.getLevelRenderState().cameraRenderState;
        for (ClientDrinks.Drink drink : drinks) {
            Entity drinker = level.getEntity(drink.playerId());
            if (drinker != null) {
                Vec3 look = drinker.getViewVector(partialTick);
                Vec3 pull = drink.layout().pullToward(look, ticks);
                DrinkLayout.Aim aim = new DrinkLayout.Aim(drinker.getEyePosition(partialTick), look);
                streamsOf(level, drink, new Frame(gloveOf(mc, drinker, partialTick, sight), pull, aim, ticks));
            }
        }
    }

    private static void forgetAllBut(List<ClientDrinks.Drink> drinks) {
        Set<Integer> drinkers = new HashSet<>();
        drinks.forEach(drink -> drinkers.add(drink.playerId()));
        LAST.keySet().retainAll(drinkers);
        SHOWING.retainAll(drinkers);
    }

    /**
     * Where the drinker's goo blob sits: the local player's as the glove
     * renderer last captured it in first person, carried to where the level's
     * projection shows the glove the hand's pass draws, and another's at their
     * hand.
     *
     * @param mc          the client
     * @param drinker     the drinking player
     * @param partialTick the partial tick
     * @param sight       the camera's state this frame
     * @return the glove's world position
     */
    static Vec3 gloveOf(Minecraft mc, Entity drinker, float partialTick, CameraRenderState sight) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (drinker == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 hand = GloveAim.handPosition(camera).subtract(sight.pos);
            return sight.pos.add(inLevelSight(hand, sight.viewRotationMatrix, sight.projectionMatrix, sight.hudFov));
        }
        Vec3 look = drinker.getViewVector(partialTick);
        Vec3 right = look.cross(UP);
        right = right.lengthSqr() > 0 ? right.normalize() : Vec3.ZERO;
        return drinker.getEyePosition(partialTick).add(look.scale(GLOVE_AHEAD)).add(right.scale(GLOVE_RIGHT))
                .subtract(0, GLOVE_BELOW, 0);
    }

    /**
     * Where the level's projection shows a point where the hand's projection
     * shows another: the hand's pass draws the glove with a field of view of
     * its own, so the point the glove sits at in the hand's sight is carried
     * square to the view, keeping its depth, by how much wider the level's
     * field of view is, and the stream's end lands on the glove the player
     * sees.
     * decision diagnose-then-fix-unmake-seam-and-ribbing
     *
     * @param handPoint       a camera-relative point as the hand's pass places it
     * @param view            the level's view rotation
     * @param levelProjection the level's projection
     * @param handFov         the hand's field of view, in degrees
     * @return the camera-relative point the level's pass shows at the same place on the screen
     */
    static Vec3 inLevelSight(Vec3 handPoint, Matrix4fc view, Matrix4fc levelProjection, float handFov) {
        double widening = 1 / (levelProjection.m11() * Math.tan(Math.toRadians(handFov) * HALF));
        Vector3f seen = view.transformDirection(new Vector3f((float) handPoint.x, (float) handPoint.y,
                (float) handPoint.z), new Vector3f());
        seen.set((float) (seen.x * widening), (float) (seen.y * widening), seen.z);
        Vector3f back = new Matrix4f(view).invert().transformDirection(seen);
        return new Vec3(back.x, back.y, back.z);
    }

    /**
     * Builds one drink's streams this frame: its tree built for the frame and kept
     * for the next, the drink showing while any stream has liquid left.
     *
     * @param level the client level
     * @param drink the drink
     * @param frame the frame
     */
    private static void streamsOf(ClientLevel level, ClientDrinks.Drink drink, Frame frame) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        List<DrinkTree.Block> blocks = blocksOf(level, drink, states);
        Map<BlockPos, DrinkTree.Stream> last = LAST.getOrDefault(drink.playerId(), Map.of());
        drink.layout().place(states.keySet(), frame.glove(), frame.aim(), frame.ticks(),
                DrinkTree.flowingOf(last, states.keySet()));
        Map<BlockPos, DrinkTree.Stream> built = new HashMap<>();
        boolean flowing = false;
        for (DrinkTree.Stream stream : DrinkTree.build(blocks, drink.layout(), frame.glove(), frame.pull(),
                frame.ticks())) {
            built.put(stream.block().pos(), stream);
            flowing |= !stream.spent();
        }
        LAST.put(drink.playerId(), built);
        if (flowing) {
            SHOWING.add(drink.playerId());
        } else {
            SHOWING.remove(drink.playerId());
        }
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
