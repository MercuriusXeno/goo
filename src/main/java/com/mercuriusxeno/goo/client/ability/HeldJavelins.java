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
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
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
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            LocalPlayer local = mc.player;
            counted(payload.casterId(), payload.formed(), mc.level.getGameTime(),
                    local == null ? StackSounds.NO_ONE : local.getId(), StackSounds.selectedBy(local),
                    cue -> StackSounds.playAt(local, cue));
        });
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
     */
    static void counted(int casterId, int formed, long now, int localId, FormSounds sounds,
                        Consumer<SoundCue> played) {
        int before = formedBy(casterId);
        formed(casterId, formed, now);
        if (casterId == localId && formed > before) {
            sounds.form().ifPresent(played);
        }
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
            return was.lerp(slot, HoveringKnives.easedAt(age));
        }
        Vec3 spawn = StackHover.pointOf(cube, xRot, yRot, StackHover.spawnSlot(index, StackHover.JAVELINS));
        return spawn.lerp(slot, HoveringKnives.easedAt(age));
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
        return index < stack.previous() ? 1f : HoveringKnives.easedAt(age);
    }

    /** Drops every stack, as a disconnect does. */
    public static void clear() {
        STACKS.clear();
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
        if (mc.level == null || STACKS.isEmpty()) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Map.Entry<Integer, Stack> entry : STACKS.entrySet()) {
            if (mc.level.getEntity(entry.getKey()) instanceof Player caster) {
                drawStack(event.getPoseStack(), buffers, caster, entry.getValue(), now, partial, camera);
            }
        }
        buffers.endBatch(GooSubmitter.renderType());
    }

    private static void drawStack(PoseStack poseStack, MultiBufferSource buffers, Player caster, Stack stack,
                                  double now, float partial, Vec3 camera) {
        float xRot = caster.getViewXRot(partial);
        float yRot = caster.getViewYRot(partial);
        Vec3 cube = StackAnchor.cubeOf(caster, partial);
        Vec3 crosshair = ThrownKnife.crosshairPointOf(caster, partial);
        double age = now - stack.changedAt();
        for (int index = 0; index < stack.count(); index++) {
            Vec3 tail = javelinAt(stack, index, cube, xRot, yRot, age);
            Vec3 heading = ThrownKnife.headingFrom(tail, crosshair);
            float scale = javelinDrawnScaleAt(stack, index, age);
            Vec3 at = ThrownKnife.drawnFromOf(tail, heading, true, scale).subtract(camera);
            poseStack.pushPose();
            poseStack.translate(at.x, at.y, at.z);
            GooFlightRenderer.emitTravelForm(poseStack.last(), buffers.getBuffer(GooSubmitter.renderType()),
                    gooTypeOf(caster), TravelForm.DART, heading, scale);
            poseStack.popPose();
        }
    }

    // The goo a caster's javelins draw in: the glove's selected type, Metal's where it names none.
    private static ResourceKey<GooTypeDefinition> gooTypeOf(Player caster) {
        GloveSelection selection = GooCasters.heldSelection(caster);
        ResourceKey<GooTypeDefinition> type = selection == null ? null : selection.getGooType();
        return type == null ? GooTypes.METAL : type;
    }
}
