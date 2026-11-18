package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.FormSounds;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.crystal.StackHover;
import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.network.StackFormedPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Knives' formed knives on this client, hovering in an upside-down pyramid
 * over each caster's lifted blob cube as the server counts them: each new
 * knife spawns at the cube's top, growing as it rises to its slot, and the
 * knives already formed stand at theirs. A count of 0 drops the pyramid, so
 * the knives vanish as right click is let go.
 * decision knives-hover-in-an-upside-down-bowling-pin-pyramid
 * decision knives-hover-above-the-blob-cube-over-the-hand
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-form-on-right-click-and-throw-on-left-click
 */@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HoveringKnives {

    /** Ticks a knife takes to ease from the blob, or from its old slot, to its slot. */
    static final double EASE_TICKS = 6;
    /** The knives a full pyramid holds. */
    private static final int KNIVES = 10;
    private static final float SMOOTH_SQUARE = 3f;
    private static final float SMOOTH_CUBE = 2f;
    private static final double HALF = 0.5;

    /** Each caster's fan, by entity id. */
    private static final Map<Integer, Fan> FANS = new HashMap<>();

    private HoveringKnives() {
    }

    /**
     * A caster's fan: the knives formed, the count before the last change,
     * and the game time it changed.
     *
     * @param count     the knives formed
     * @param previous  the knives formed before the change
     * @param changedAt the game time of the change
     */
    record Fan(int count, int previous, long changedAt) {
    }

    /**
     * Takes the count the server sent for a caster.
     *
     * @param payload the formed count
     * @param context the network context
     */
    public static void onPayload(StackFormedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                LocalPlayer local = mc.player;
                counted(payload.casterId(), payload.formed(), mc.level.getGameTime(),
                        local == null ? StackSounds.NO_ONE : local.getId(), StackSounds.selectedBy(local),
                        cue -> StackSounds.playAt(local, cue));
            }
        });
    }

    /**
     * Records a caster's formed count and sounds each knife the local
     * player's hold forms: a count risen for the local player plays the form
     * cue once, and a count that holds or falls, or another caster's, plays none.
     * decision knives-and-javelins-sound-as-each-forms-and-is-thrown
     *
     * @param casterId the caster's entity id
     * @param formed   the knives formed
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
     * Records a caster's formed count at a game time: 0 drops the fan.
     *
     * @param casterId the caster's entity id
     * @param formed   the knives formed
     * @param now      the game time
     */
    static void formed(int casterId, int formed, long now) {
        if (formed <= 0) {
            FANS.remove(casterId);
            return;
        }
        Fan was = FANS.get(casterId);
        FANS.put(casterId, new Fan(formed, was == null ? 0 : was.count(), now));
    }

    /**
     * A caster's fan.
     *
     * @param casterId the caster's entity id
     * @return the fan, or null where the caster holds no knives
     */
    static @Nullable Fan fanOf(int casterId) {
        return FANS.get(casterId);
    }

    /**
     * The knives in a caster's fan, as the server last counted them.
     *
     * @param casterId the caster's entity id
     * @return the knives, 0 for a caster holding none
     */
    public static int formedBy(int casterId) {
        Fan fan = FANS.get(casterId);
        return fan == null ? 0 : fan.count();
    }

    /** Drops every fan, as a disconnect does. */
    public static void clear() {
        FANS.clear();
    }

    /**
     * Where a knife of the pyramid stands an age after its last change over
     * the cube: at its slot for a knife formed before the change, and for a
     * knife the change formed, rising from the cube's top as it forms to its slot.
     * knives-hover-above-the-blob-cube-over-the-hand
     *
     * @param fan   the pyramid's count
     * @param index the knife's index
     * @param cube  the lifted blob cube's center
     * @param xRot  the caster's view pitch in degrees
     * @param yRot  the caster's view yaw in degrees
     * @param age   ticks since the change, with the partial tick
     * @return the knife's point
     */
    static Vec3 knifeAt(Fan fan, int index, Vec3 cube, float xRot, float yRot, double age) {
        Vec3 slot = StackHover.pointOf(cube, xRot, yRot, StackHover.knifeSlot(index, fan.count()));
        if (index < fan.previous()) {
            // knives-hover-in-an-upside-down-bowling-pin-pyramid: a standing knife shifts as the centered pile widens
            Vec3 was = StackHover.pointOf(cube, xRot, yRot, StackHover.knifeSlot(index, fan.previous()));
            return was.lerp(slot, easedAt(age));
        }
        // weapons-form-from-a-goo-cuboid-leaving-the-palm-blob: the cuboid leaves the blob's middle for the slot
        return cube.lerp(slot, easedAt(age));
    }
    /**
     * A knife's size an age after the fan's last change: whole for a knife
     * formed before it, growing out of the blob for one the change formed.
     *
     * @param fan   the fan
     * @param index the knife's index
     * @param age   ticks since the change, with the partial tick
     * @return the size, 1 at full
     */
    static float knifeScaleAt(Fan fan, int index, double age) {
        return index < fan.previous() ? 1f : easedAt(age);
    }

    /**
     * The size a hovering knife is drawn at against a thrown knife's: whole
     * once formed, as large hovering as thrown.
     *
     * @param fan   the pyramid's count
     * @param index the knife's index
     * @param age   ticks since the change, with the partial tick
     * @return the drawn size, 1 a thrown knife's
     */
    static float knifeDrawnScaleAt(Fan fan, int index, double age) {
        return knifeScaleAt(fan, index, age);
    }

    /**
     * The order a pyramid's knives draw in, so the clear glass of a nearer
     * knife lays over a farther one: farthest from the camera first.
     *
     * @param middles each knife's middle, by index
     * @param camera  the camera's position
     * @return the knives' indices in the order they draw
     */
    static List<Integer> drawOrderOf(List<Vec3> middles, Vec3 camera) {
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < middles.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingDouble((Integer index) -> middles.get(index).distanceToSqr(camera)).reversed());
        return order;
    }

    /**
     * Whether a caster's stack draws in the level's pass: every caster's but
     * the local player's in first person, whose stack the hand's pass draws
     * with the lifted blob.
     *
     * @param caster the caster
     * @return true where the level draws it
     */
    static boolean drawsInTheLevel(Player caster) {
        Minecraft mc = Minecraft.getInstance();
        return caster != mc.player || !mc.options.getCameraType().isFirstPerson();
    }

    static float easedAt(double age) {
        return easedOver(age, EASE_TICKS);
    }

    /**
     * How far an ease in and out has run: none at its start, all at its end,
     * behind a straight line near its start and ahead of it near its end.
     *
     * @param age   ticks since it began, with the partial tick
     * @param ticks the ticks it takes
     * @return the share run, 0 to 1
     */
    static float easedOver(double age, double ticks) {
        float share = (float) Math.clamp(age / ticks, 0.0, 1.0);
        return share * share * (SMOOTH_SQUARE - SMOOTH_CUBE * share);
    }

    /**
     * Draws every caster's pyramid after the level, clouds included, so no
     * cloud shows through a knife, each knife its butt on its slot and its
     * point running forward at the crosshair's point, the line it flies along.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        LevelOverlayView.inView(event, () -> drawEvery(event));
    }

    private static void drawEvery(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || FANS.isEmpty()) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + partial;
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.CRYSTAL_SHARD_TYPE));
        for (Map.Entry<Integer, Fan> entry : FANS.entrySet()) {
            if (mc.level.getEntity(entry.getKey()) instanceof Player caster && drawsInTheLevel(caster)) {
                drawFan(quads, caster, entry.getValue(), now - entry.getValue().changedAt(), partial, camera);
            }
        }
        buffers.endBatch(GooRenderTypes.CRYSTAL_SHARD_TYPE);
        for (Map.Entry<Integer, Fan> entry : FANS.entrySet()) {
            if (mc.level.getEntity(entry.getKey()) instanceof Player caster && drawsInTheLevel(caster)) {
                drawCuboids(event.getPoseStack().last(), buffers.getBuffer(GooSubmitter.renderType()), caster,
                        entry.getValue(), now - entry.getValue().changedAt(), partial, camera);
            }
        }
        buffers.endBatch(GooSubmitter.renderType());
    }

    /**
     * Draws the cuboid each forming knife is cut from the palm blob as, where
     * the knife is on its way to its slot, shrinking as the knife grows into
     * its shape, so no fully formed knife draws at the blob.
     * decision weapons-form-from-a-goo-cuboid-leaving-the-palm-blob
     *
     * @param pose    the pose camera-relative points stand in
     * @param c       a consumer of the translucent goo render type
     * @param caster  the caster
     * @param fan     the caster's pyramid
     * @param age     ticks since the pyramid's last change, with the partial tick
     * @param partial the frame's partial tick
     * @param camera  the camera's position
     */
    static void drawCuboids(PoseStack.Pose pose, VertexConsumer c, Player caster, Fan fan, double age,
                            float partial, Vec3 camera) {
        Vec3 cube = StackAnchor.cubeOf(caster, partial);
        float xRot = caster.getViewXRot(partial);
        float yRot = caster.getViewYRot(partial);
        for (int index = fan.previous(); index < fan.count(); index++) {
            float weapon = knifeScaleAt(fan, index, age);
            if (weapon < 1f) {
                GooFlightRenderer.emitCuboidAt(pose, c, gooTypeOf(caster),
                        knifeAt(fan, index, cube, xRot, yRot, age).subtract(camera),
                        (float) StackHover.cuboidEdgeOf(KNIVES) * (1f - weapon));
            }
        }
    }

    // The goo a caster's knives are cut from: the selected type, Crystal's where it names none.
    private static ResourceKey<GooTypeDefinition> gooTypeOf(Player caster) {
        GloveSelection selection = GooCasters.heldSelection(caster);
        ResourceKey<GooTypeDefinition> type = selection == null ? null : selection.getGooType();
        return type == null ? GooTypes.CRYSTAL : type;
    }

    /**
     * Submits the local first-person player's pyramid in the hand's pass,
     * with the lifted blob, laid out as the level would lay it and carried
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
        Fan fan = FANS.get(caster.getId());
        if (fan == null || caster.level() == null) {
            return;
        }
        double age = caster.level().getGameTime() + partial - fan.changedAt();
        collector.submitCustomGeometry(poseStack, GooRenderTypes.CRYSTAL_SHARD_TYPE,
                (pose, consumer) -> drawFan(new FlatQuadContext(pose, consumer), caster, fan, age, partial, camera));
        collector.submitCustomGeometry(poseStack, GooSubmitter.renderType(),
                (pose, consumer) -> drawCuboids(pose, consumer, caster, fan, age, partial, camera));
    }

    private static void drawFan(FlatQuadContext quads, Player caster, Fan fan, double age, float partial,
                                Vec3 camera) {
        float xRot = caster.getViewXRot(partial);
        float yRot = caster.getViewYRot(partial);
        Vec3 cube = StackAnchor.cubeOf(caster, partial);
        Vec3 crosshair = ThrownKnife.aimPointOf(caster, partial);
        List<Vec3> butts = new ArrayList<>();
        List<Vec3> middles = new ArrayList<>();
        for (int index = 0; index < fan.count(); index++) {
            Vec3 butt = knifeAt(fan, index, cube, xRot, yRot, age);
            butts.add(butt);
            middles.add(butt.lerp(crosshair, StackHover.KNIFE_LENGTH * HALF / butt.distanceTo(crosshair)));
        }
        // knives-hover-above-the-blob-cube-over-the-hand: farthest first, so nearer glass lays over farther
        for (int index : drawOrderOf(middles, camera)) {
            Vec3 heading = ThrownKnife.headingFrom(butts.get(index), crosshair);
            float scale = knifeDrawnScaleAt(fan, index, age);
            GlassKunai.emit(quads, ThrownKnife.drawnFromOf(butts.get(index), heading, false, scale).subtract(camera),
                    heading, 0f, scale);
        }
    }
}
