package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.FormSounds;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.ability.crystal.StackHover;
import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.network.StackFormedPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dart's stacked javelins on this client, as the server counts them for each
 * caster: they hover side by side over the caster's lifted blob cube, each
 * pointing along the look, each new one spawning at the cube's top and
 * growing as it rises to its slot. The local player's left click throws only
 * while one stands formed, and the shink plays as each of the local player's
 * javelins forms.
 * decision dart-stacks-three-javelins-one-per-ten-ticks
 * decision dart-javelins-hover-over-the-shrinking-blob-cube
 * decision dart-shinks-as-it-forms-and-pitched-down-as-it-reverts
 * decision dart-flutes-draw-as-fins
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HeldJavelins {

    /** Ticks a javelin takes to rise from the cube's top to its slot. */
    static final double EASE_TICKS = HoveringKnives.EASE_TICKS;

    /** Each caster's stack, by entity id. */
    private static final Map<Integer, Stack> STACKS = new HashMap<>();
    /** Each caster's released stack returning to the palm blob, by entity id. */
    private static final Map<Integer, StackReturn.Released> RETURNS = new HashMap<>();
    /** Ticks a javelin takes to form, Dart's form interval (metal_dart.json's stack_ticks). */
    static final double FORM_TICKS = 10;

    private HeldJavelins() {
    }

    /**
     * A caster's stack: the javelins formed, the count before the last
     * change, and the game time it changed.
     *
     * @param count     the javelins formed
     * @param previous  the javelins formed before the change
     * @param changedAt the game time of the change
     */
    record Stack(int count, int previous, long changedAt) {
    }

    /**
     * Takes the count the server sent for a caster, shinking for each new
     * javelin of the local player's.
     *
     * @param payload the formed count
     * @param context the network context
     */
    public static void onPayload(StackFormedPayload payload, IPayloadContext context) {
        StackSounds.take(payload, context, HeldJavelins::counted);
    }

    /**
     * Records a caster's formed count and sounds each javelin the local
     * player's hold forms: a count risen for the local player plays the form
     * cue, Dart's shink, once, and a count that holds or falls, or another
     * caster's, plays none.
     * decision dart-shinks-as-it-forms-and-pitched-down-as-it-reverts
     *
     * @param casterId the caster's entity id
     * @param formed   the javelins formed
     * @param now      the game time
     * @param localId  the local player's entity id
     * @param sounds   the local player's selected stack sounds
     * @param played   plays a cue at the local player
     * @param released whether the count fell because the hold ended, every javelin held returning to the blob
     */
    static void counted(int casterId, int formed, long now, int localId, FormSounds sounds,
                        Consumer<SoundCue> played, boolean released) {
        int before = formedBy(casterId);
        StackReturn.noteRelease(RETURNS, casterId, before, formed, now, released);
        formed(casterId, formed, now);
        StackSounds.soundForming(casterId, localId, before, formed, sounds, played);
    }

    /**
     * The javelins a caster released returning to the palm blob, or null where none return.
     *
     * @param casterId the caster's entity id
     * @return the return
     */
    static StackReturn.@Nullable Released returnOf(int casterId) {
        return RETURNS.get(casterId);
    }


    /**
     * Records a caster's formed count at a game time: 0 drops the stack.
     *
     * @param casterId the caster's entity id
     * @param formed   the javelins formed
     * @param now      the game time
     */
    static void formed(int casterId, int formed, long now) {
        if (formed <= 0) {
            STACKS.remove(casterId);
            return;
        }
        Stack was = STACKS.get(casterId);
        STACKS.put(casterId, new Stack(formed, was == null ? 0 : was.count(), now));
    }

    /**
     * A caster's stack.
     *
     * @param casterId the caster's entity id
     * @return the stack, or null where the caster holds no javelins
     */
    static @Nullable Stack stackOf(int casterId) {
        return STACKS.get(casterId);
    }

    /**
     * The javelins a caster holds formed, as the server last counted them.
     *
     * @param casterId the caster's entity id
     * @return the javelins, 0 for a caster holding none
     */
    public static int formedBy(int casterId) {
        Stack stack = STACKS.get(casterId);
        return stack == null ? 0 : stack.count();
    }

    /**
     * Where a javelin of the stack stands an age after its last change over
     * the cube: at its slot for one formed before the change, and for one the
     * change formed, rising from the cube's top as it forms to its slot.
     *
     * @param stack the stack
     * @param index the javelin's index
     * @param cube  the lifted blob cube's center
     * @param xRot  the caster's view pitch in degrees
     * @param yRot  the caster's view yaw in degrees
     * @param age   ticks since the change, with the partial tick
     * @return the javelin's point
     */
    static Vec3 javelinAt(Stack stack, int index, Vec3 cube, float xRot, float yRot, double age) {
        Vec3 slot = StackHover.pointOf(cube, xRot, yRot, StackHover.javelinSlot(index, stack.count()));
        if (index < stack.previous()) {
            // dart-javelins-hover-over-the-shrinking-blob-cube: a standing javelin shifts as the centered pile widens
            Vec3 was = StackHover.pointOf(cube, xRot, yRot, StackHover.javelinSlot(index, stack.previous()));
            return was.lerp(slot, formedAt(age));
        }
        // weapons-form-from-a-goo-cuboid-leaving-the-palm-blob: the cuboid leaves the blob's middle for the slot
        return cube.lerp(slot, formedAt(age));
    }

    /**
     * The size a hovering javelin is drawn at against a thrown javelin's: whole once formed.
     *
     * @param stack the stack
     * @param index the javelin's index
     * @param age   ticks since the change, with the partial tick
     * @return the drawn size, 1 a thrown javelin's
     */
    static float javelinDrawnScaleAt(Stack stack, int index, double age) {
        return index < stack.previous() ? 1f : formedAt(age);
    }

    /**
     * How far a javelin's forming has run: eased in and out over the form
     * interval, Dart's 10 ticks, so it stands formed before the next begins.
     *
     * @param age ticks since the change, with the partial tick
     * @return the share run, 0 to 1
     */
    static float formedAt(double age) {
        return HoveringKnives.easedOver(age, FORM_TICKS);
    }

    /** Drops every stack, as a disconnect does. */
    public static void clear() {
        STACKS.clear();
        RETURNS.clear();
    }

    /**
     * Draws every caster's stack after the level, clouds included, so no cloud
     * shows through a javelin, each as the dart form in its caster's goo,
     * pointing along the caster's look.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        LevelOverlayView.inView(event, () -> drawEvery(event));
    }

    private static void drawEvery(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || STACKS.isEmpty() && RETURNS.isEmpty()) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        RETURNS.values().removeIf(release -> release.doneAt(now));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        List<Integer> casters = new ArrayList<>(STACKS.keySet());
        RETURNS.keySet().stream().filter(id -> !STACKS.containsKey(id)).forEach(casters::add);
        for (Integer casterId : casters) {
            if (mc.level.getEntity(casterId) instanceof Player caster && HoveringKnives.drawsInTheLevel(caster)) {
                ResourceKey<GooTypeDefinition> type = gooTypeOf(caster);
                List<HoveringKnives.Piece> pieces = piecesOf(caster, now, partial);
                layStack(event.getPoseStack(), caster, pieces, partial, camera,
                        (at, heading, scale) -> GooFlightRenderer.emitTravelForm(at.last(),
                                buffers.getBuffer(GooSubmitter.renderType()), type, TravelForm.DART, heading, scale));
                drawCuboids(event.getPoseStack().last(), buffers.getBuffer(GooSubmitter.renderType()), caster,
                        pieces, camera);
            }
        }
        buffers.endBatch(GooSubmitter.renderType());
    }

    /**
     * A caster's javelins this frame: the triangle standing and forming, and
     * any released javelins on their way back into the palm blob, each
     * standing at its slot until its return starts, then travelling to the
     * blob's middle as it turns back into its cuboid.
     * decision released-weapons-turn-back-into-blobs-that-rejoin-the-palm-blob
     *
     * @param caster  the caster
     * @param now     the game time, with the partial tick
     * @param partial the frame's partial tick
     * @return the javelins, each its tail's place and its javelin shape's weight
     */
    static List<HoveringKnives.Piece> piecesOf(Player caster, double now, float partial) {
        Vec3 cube = StackAnchor.cubeOf(caster, partial);
        float xRot = caster.getViewXRot(partial);
        float yRot = caster.getViewYRot(partial);
        List<HoveringKnives.Piece> pieces = new ArrayList<>();
        Stack stack = STACKS.get(caster.getId());
        if (stack != null) {
            double age = now - stack.changedAt();
            for (int index = 0; index < stack.count(); index++) {
                pieces.add(new HoveringKnives.Piece(javelinAt(stack, index, cube, xRot, yRot, age),
                        javelinDrawnScaleAt(stack, index, age)));
            }
        }
        StackReturn.Released release = RETURNS.get(caster.getId());
        if (release != null) {
            for (int index = 0; index < release.released(); index++) {
                float progress = StackReturn.progressAt(index, release.released(), now - release.at());
                Vec3 slot = StackHover.pointOf(cube, xRot, yRot, StackHover.javelinSlot(index, release.released()));
                if (progress < 1f) {
                    pieces.add(new HoveringKnives.Piece(slot.lerp(cube, StackReturn.travelAt(progress)),
                            StackReturn.weaponWeightAt(progress)));
                }
            }
        }
        return pieces;
    }

    /**
     * Submits the local first-person player's javelins in the hand's pass,
     * with the lifted blob, laid out as the level would lay them and carried
     * onto the same spot of the screen.
     *
     * @param poseStack the pose stack, its pose set onto the hand pass's camera-relative world axes
     * @param collector the hand pass's node collector
     * @param caster    the local player
     * @param partial   the frame's partial tick
     * @param camera    the camera's position
     */
    static void submitInHand(PoseStack poseStack, SubmitNodeCollector collector, Player caster, float partial,
                             Vec3 camera) {
        if (caster.level() == null || !STACKS.containsKey(caster.getId()) && !RETURNS.containsKey(caster.getId())) {
            return;
        }
        ResourceKey<GooTypeDefinition> type = gooTypeOf(caster);
        List<HoveringKnives.Piece> pieces = piecesOf(caster, caster.level().getGameTime() + partial, partial);
        layStack(poseStack, caster, pieces, partial, camera,
                (at, heading, scale) -> collector.submitCustomGeometry(at, GooSubmitter.renderType(),
                        (pose, consumer) -> GooFlightRenderer.emitTravelForm(pose, consumer, type, TravelForm.DART,
                                heading, scale)));
        collector.submitCustomGeometry(poseStack, GooSubmitter.renderType(),
                (pose, consumer) -> drawCuboids(pose, consumer, caster, pieces, camera));
    }

    /**
     * Draws the cuboid each forming or returning javelin is while it is not
     * yet, or no longer, a javelin, where it is on its way, shrinking as the
     * javelin grows into its shape and whole as it merges with the blob, so no
     * fully formed javelin draws at the blob.
     * decision weapons-form-from-a-goo-cuboid-leaving-the-palm-blob
     * decision released-weapons-turn-back-into-blobs-that-rejoin-the-palm-blob
     *
     * @param pose   the pose camera-relative points stand in
     * @param c      a consumer of the translucent goo render type
     * @param caster the caster
     * @param pieces the caster's javelins this frame
     * @param camera the camera's position
     */
    static void drawCuboids(PoseStack.Pose pose, VertexConsumer c, Player caster, List<HoveringKnives.Piece> pieces,
                            Vec3 camera) {
        for (HoveringKnives.Piece piece : pieces) {
            if (piece.weapon() < 1f) {
                GooFlightRenderer.emitCuboidAt(pose, c, gooTypeOf(caster), piece.at().subtract(camera),
                        (float) StackHover.cuboidEdgeOf(StackHover.JAVELINS) * (1f - piece.weapon()));
            }
        }
    }

    /** Draws one javelin about the pose's origin, pointed along a heading at a size. */
    @FunctionalInterface
    private interface JavelinDraw {
        void draw(PoseStack at, Vec3 heading, float scale);
    }

    private static void layStack(PoseStack poseStack, Player caster, List<HoveringKnives.Piece> pieces,
                                 float partial, Vec3 camera, JavelinDraw draw) {
        Vec3 crosshair = ThrownKnife.aimPointOf(caster, partial);
        for (HoveringKnives.Piece piece : pieces) {
            if (piece.weapon() > 0f) {
                Vec3 heading = ThrownKnife.headingFrom(piece.at(), crosshair);
                Vec3 at = ThrownKnife.drawnFromOf(piece.at(), heading, true, piece.weapon()).subtract(camera);
                poseStack.pushPose();
                poseStack.translate(at.x, at.y, at.z);
                draw.draw(poseStack, heading, piece.weapon());
                poseStack.popPose();
            }
        }
    }

    // The goo a caster's javelins draw in: the glove's selected type, Metal's where it names none.
    private static ResourceKey<GooTypeDefinition> gooTypeOf(Player caster) {
        GloveSelection selection = GooCasters.heldSelection(caster);
        ResourceKey<GooTypeDefinition> type = selection == null ? null : selection.getGooType();
        return type == null ? GooTypes.METAL : type;
    }
}