package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.unmake.MeltingBlockEntity;
import com.mercuriusxeno.goo.client.ber.MeltMeshGoo;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.entity.FormingGooBlob;
import com.mercuriusxeno.goo.network.DrinkPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
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
 * flows and the drink stays live until its last stream is spent, and keeps
 * where each forming goo blob stands for its chunks to fly to. The frame a
 * block's square lands, the block stands as solid chunks of its own faces
 * that peel away to its blob over the drain, so it is never seen vanishing.
 * decision unmake-waves-dissolve-by-crucible-cost
 * decision diagnose-then-fix-unmake-seam-and-ribbing
 * decision unmake-drink-is-a-stream-of-break-particles
 * decision unmake-peels-the-block-into-chunks-that-stream-to-the-hand
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DrinkRenderer {

    /** Where another player's glove hangs before their eyes, in blocks. */
    private static final double GLOVE_AHEAD = 0.5;
    /** How far right of another player's look their glove hangs, in blocks. */
    private static final double GLOVE_RIGHT = 0.35;
    /** How far below another player's eyes their glove hangs, in blocks. */
    private static final double GLOVE_BELOW = 0.45;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double HALF = 0.5;
    private static final float HALF_F = 0.5f;
    /** The pitch vanilla plays a block's break sound at, as a share of the block's own. */
    private static final float BREAK_PITCH = 0.8f;
    /** Each drink's streams as last built, by its drinker then block, which say where liquid still flows. */
    private static final Map<Integer, Map<BlockPos, DrinkTree.Stream>> LAST = new HashMap<>();
    /** The drinkers whose drink still showed liquid last frame. */
    private static final Set<Integer> SHOWING = new HashSet<>();
    /** Which blocks of each drink have begun to peel, by its drinker. */
    private static final Map<Integer, DrinkLandings> LANDINGS = new HashMap<>();
    /** Where each blob a drink streams to last stood, by its entity id, for chunks still flying once it is gone. */
    private static final Map<Integer, Vec3> BLOB_POINTS = new HashMap<>();

    private DrinkRenderer() {
    }

    /**
     * What a frame builds a drink against.
     *
     * @param look        the drinker's look
     * @param aim         the drinker's aim line, which picks the spine
     * @param ticks       the game time including the partial tick
     * @param partialTick the partial tick
     */
    private record Frame(Vec3 look, DrinkLayout.Aim aim, double ticks, float partialTick) {
    }

    /**
     * Builds every drink's trees this frame, after the level.
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
        for (ClientDrinks.Drink drink : drinks) {
            Entity drinker = level.getEntity(drink.playerId());
            if (drinker != null) {
                Vec3 look = drinker.getViewVector(partialTick);
                DrinkLayout.Aim aim = new DrinkLayout.Aim(drinker.getEyePosition(partialTick), look);
                streamsOf(level, drink, new Frame(look, aim, ticks, partialTick));
            }
        }
    }

    private static void forgetAllBut(List<ClientDrinks.Drink> drinks) {
        Set<Integer> drinkers = new HashSet<>();
        Set<Integer> blobs = new HashSet<>();
        drinks.forEach(drink -> {
            drinkers.add(drink.playerId());
            drink.streaming().forEach(streaming -> blobs.add(streaming.blob()));
        });
        LAST.keySet().retainAll(drinkers);
        SHOWING.retainAll(drinkers);
        LANDINGS.keySet().retainAll(drinkers);
        BLOB_POINTS.keySet().retainAll(blobs);
        DrinkPeels.CLIENT.keepDestinationsOf(blobs);
    }

    /**
     * Where a blob a drink streams to stands this frame: the forming blob
     * itself, or where it last stood once it is gone, or the first of its
     * blocks before the client has seen it.
     * unmake-converges-near-the-unmade-blocks-into-a-goo-item
     *
     * @param level       the client level
     * @param blob        the blob's entity id
     * @param blocks      the blocks streaming to it
     * @param partialTick the partial tick
     * @return the point its streams and chunks end at
     */
    private static Vec3 blobPoint(ClientLevel level, int blob, List<DrinkTree.Block> blocks, float partialTick) {
        if (level.getEntity(blob) instanceof FormingGooBlob forming) {
            Vec3 at = forming.getPosition(partialTick);
            BLOB_POINTS.put(blob, at);
            return at;
        }
        return BLOB_POINTS.getOrDefault(blob, blocks.getFirst().center());
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
     * Builds one drink's streams this frame: a tree for each blob its blocks
     * stream to, built for the frame and kept for the next, the drink showing
     * while any stream has liquid left.
     * unmake-converges-near-the-unmade-blocks-into-a-goo-item
     *
     * @param level the client level
     * @param drink the drink
     * @param frame the frame
     */
    private static void streamsOf(ClientLevel level, ClientDrinks.Drink drink, Frame frame) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        Map<Integer, List<DrinkTree.Block>> byBlob = blocksByBlob(level, drink, states);
        Map<BlockPos, DrinkTree.Stream> last = LAST.getOrDefault(drink.playerId(), Map.of());
        Map<BlockPos, DrinkTree.Stream> built = new HashMap<>();
        boolean flowing = false;
        DrinkLandings landings = LANDINGS.computeIfAbsent(drink.playerId(), ignored -> new DrinkLandings());
        for (Map.Entry<Integer, List<DrinkTree.Block>> group : byBlob.entrySet()) {
            int blob = group.getKey();
            Vec3 point = blobPoint(level, blob, group.getValue(), frame.partialTick());
            DrinkLayout layout = drink.layoutFor(blob);
            Set<BlockPos> laid = new HashSet<>();
            group.getValue().forEach(block -> laid.add(block.pos()));
            layout.place(laid, point, frame.aim(), frame.ticks(), DrinkTree.flowingOf(last, laid));
            Vec3 pull = layout.pullToward(frame.look(), frame.ticks());
            for (DrinkTree.Stream stream : DrinkTree.build(group.getValue(), layout, point, pull, frame.ticks())) {
                built.put(stream.block().pos(), stream);
                flowing |= !stream.spent();
                if (landings.peelsAt(stream.block(), frame.ticks())) {
                    peel(level, blob, stream.block(), states.get(stream.block().pos()));
                }
            }
            DrinkPeels.CLIENT.destination(blob, point);
        }
        LAST.put(drink.playerId(), built);
        showing(drink.playerId(), flowing);
    }

    /**
     * @param drinker the drinker
     * @param flowing whether any stream of their drink still has liquid left, which keeps the drink live
     */
    private static void showing(int drinker, boolean flowing) {
        if (flowing) {
            SHOWING.add(drinker);
        } else {
            SHOWING.remove(drinker);
        }
    }

    private static Map<Integer, List<DrinkTree.Block>> blocksByBlob(ClientLevel level, ClientDrinks.Drink drink,
                                                                  Map<BlockPos, BlockState> states) {
        Map<Integer, List<DrinkTree.Block>> byBlob = new HashMap<>();
        for (DrinkPayload.Streaming streaming : drink.streaming()) {
            BlockState state = ClientDrinks.CLIENT.blockOf(streaming.pos(), seenAt(level, streaming.pos()));
            if (state != null) {
                states.put(streaming.pos(), state);
                byBlob.computeIfAbsent(streaming.blob(), ignored -> new ArrayList<>())
                        .add(new DrinkTree.Block(streaming.pos(), Vec3.atCenterOf(streaming.pos()),
                                DrinkTree.scaleOf(MeltMeshGoo.volumeOf(state)), streaming.picked(), streaming.start(),
                                streaming.end()));
            }
        }
        return byBlob;
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
     * Peels a block into its chunks with its own break sound, as vanilla
     * plays it: the chunks fill the block's outline and stand in its place
     * wearing its own faces, crumbling off one by one to fly to its blob
     * where the blob stands frame by frame.
     *
     * @param level the client level
     * @param blob  the entity id of the blob its chunks fly to
     * @param block the block
     * @param state the block as it stood
     */
    private static void peel(ClientLevel level, int blob, DrinkTree.Block block, BlockState state) {
        BlockPos pos = block.pos();
        SoundType sound = state.getSoundType(level, pos, null);
        level.playLocalSound(pos, sound.getBreakSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) * HALF_F,
                sound.getPitch() * BREAK_PITCH, false);
        VoxelShape shape = state.getShape(level, pos);
        AABB outline = shape.isEmpty() ? Shapes.block().bounds() : shape.bounds();
        DrinkRide.Breakup breakup = DrinkRide.chunksOf(block, outline, level.getRandom());
        DrinkPeels.CLIENT.begin(new DrinkPeels.Peel(blob, pos.immutable(), breakup, PeelFaces.of(level, pos, state),
                block.end() + (double) DrinkStream.LONGEST_TRAVEL_TICKS));
    }
}
