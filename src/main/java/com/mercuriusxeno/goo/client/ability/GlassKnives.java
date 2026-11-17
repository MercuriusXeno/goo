package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.network.DripFlights;
import com.mercuriusxeno.goo.network.GlassKnifePayload;
import com.mercuriusxeno.goo.throwing.DripArc;
import com.mercuriusxeno.goo.throwing.FenceRails;
import com.mercuriusxeno.goo.throwing.ProjectileFlight;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Knives' glass knives in flight on this client: each leaves its hover slot
 * as the glass kunai it hovered as and flies the projectile flight the
 * server flies it on, point first, rolling as it goes, dropping like an
 * arrow and passing between a fence's rails. One meeting a block stands
 * stuck there a moment, then shatters; one meeting a mob shatters at once,
 * and one whose flight runs out shatters in the air, with a tinkle of glass,
 * cracking into splinters that tumble down and melt away.
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-and-dart-function-as-projectiles
 * decision knives-and-javelins-drop-like-an-arrow
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GlassKnives {

    /** Ticks a knife stuck in a block stands before it shatters. */
    static final int STUCK_TICKS = 30;
    /** Radians a knife rolls about its heading each tick it flies. */
    private static final float ROLL_PER_TICK = 0.9f;
    /** How deep a stuck knife's point sinks into the block, in blocks. */
    private static final double SINK = 0.08;
    private static final float STICK_VOLUME = 0.35f;
    private static final float SHATTER_VOLUME = 0.3f;
    private static final float STRIKE_VOLUME = 0.6f;
    private static final float PITCH_LOW = 1.3f;
    private static final float PITCH_SPAN = 0.5f;
    /** The heading of a knife with no path to read one from. */
    private static final Vec3 DOWN = new Vec3(0, -1, 0);

    private static final List<Knife> LIVE = new ArrayList<>();
    /** The splinters shattered knives crack into. */
    private static final GlassSlivers SLIVERS = new GlassSlivers();

    private GlassKnives() {
    }

    /** One knife: its tick points, velocity and thrower, how its flight ended, and when it left. */
    private static final class Knife {
        private final List<Vec3> path = new ArrayList<>();
        private final int throwerId;
        private final long startTick;
        /** The goo a javelin draws in, null for a glass knife. */
        private final @Nullable ResourceKey<GooTypeDefinition> javelin;
        private Vec3 velocity;
        private GlassKnifePayload.@Nullable Ending ending;

        Knife(GlassKnifePayload thrown, long startTick) {
            path.add(thrown.start());
            this.throwerId = thrown.throwerId();
            this.velocity = thrown.velocity();
            this.startTick = startTick;
            this.javelin = GlassKnifePayload.GLASS.equals(thrown.javelinType()) ? null
                    : GooTypes.known(thrown.javelinType());
        }

        int ticks() {
            return path.size() - 1;
        }

        /**
         * Flies the knife one tick, or lets a stuck one stand and shatter.
         *
         * @param level the client level
         * @param now   the game time
         * @return true once the knife is gone
         */
        boolean tick(ClientLevel level, long now) {
            if (ending == null) {
                fly(level);
                return ending != null && ending != GlassKnifePayload.Ending.STUCK;
            }
            boolean gone = now - startTick >= ticks() + STUCK_TICKS;
            if (gone && javelin == null) {
                shatter(level, path.getLast(), SoundEvents.GLASS_BREAK, SHATTER_VOLUME);
            }
            return gone;
        }

        private void fly(ClientLevel level) {
            DripArc.Step step = ProjectileFlight.step(path.getLast(), velocity, ProjectileFlight.ARROW,
                    FenceRails.clipping(level), DripFlights.livingNear(level, throwerId),
                    DripFlights.airspaceOf(level));
            path.add(step.position());
            velocity = step.velocity();
            endingOf(step, ticks()).ifPresent(ended -> {
                ending = ended;
                if (javelin == null) {
                    land(level, ended, path.getLast());
                }
            });
        }
    }

    /**
     * How a tick of a knife's flight ends it: in a block it meets, on a mob
     * it meets, or spent where it left the world or its flight ran out.
     *
     * @param step  the tick of its flight
     * @param ticks the ticks the knife has flown, this one included
     * @return how the flight ends, empty while it flies on
     */
    static Optional<GlassKnifePayload.Ending> endingOf(DripArc.Step step, int ticks) {
        if (step.strike().isPresent()) {
            return Optional.of(GlassKnifePayload.Ending.STUCK);
        }
        if (step.living().isPresent()) {
            return Optional.of(GlassKnifePayload.Ending.STRUCK);
        }
        return step.lost() || ticks >= ThrownKnife.MOST_FLIGHT_TICKS ? Optional.of(GlassKnifePayload.Ending.SPENT)
                : Optional.empty();
    }
    /**
     * Starts a knife the server sent.
     *
     * @param payload the knife payload
     * @param context the network context
     */
    public static void onPayload(GlassKnifePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                LIVE.add(new Knife(payload, level.getGameTime()));
            }
        });
    }

    /** Drops every knife, as a disconnect does. */
    public static void clear() {
        LIVE.clear();
        SLIVERS.clear();
    }

    /**
     * Client tick: flies every knife a tick, landing one whose flight ends;
     * a stuck knife shatters once it has stood its while.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            LIVE.clear();
            return;
        }
        long now = level.getGameTime();
        LIVE.removeIf(knife -> knife.tick(level, now));
    }

    private static void land(ClientLevel level, GlassKnifePayload.Ending ending, Vec3 end) {
        switch (ending) {
            case STUCK -> level.playLocalSound(end.x, end.y, end.z, SoundEvents.AMETHYST_CLUSTER_HIT,
                    SoundSource.PLAYERS, STICK_VOLUME, pitch(level.getRandom()), false);
            case STRUCK -> shatter(level, end, SoundEvents.GLASS_BREAK, STRIKE_VOLUME);
            case SPENT -> shatter(level, end, SoundEvents.AMETHYST_CLUSTER_BREAK, SHATTER_VOLUME);
        }
    }

    private static void shatter(ClientLevel level, Vec3 at, SoundEvent sound, float volume) {
        RandomSource random = level.getRandom();
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch(random), false);
        SLIVERS.crack(at, level.getGameTime(), random);
    }

    private static float pitch(RandomSource random) {
        return PITCH_LOW + random.nextFloat() * PITCH_SPAN;
    }

    /**
     * Draws every knife after translucent blocks: in flight between its
     * tick points, or stuck at its end.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || LIVE.isEmpty() && SLIVERS.isEmpty()) {
            return;
        }
        double now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.CRYSTAL_SHARD_TYPE));
        for (Knife knife : LIVE) {
            if (knife.javelin == null) {
                drawKnife(quads, knife, now - knife.startTick - 1, camera);
            } else {
                drawJavelin(event.getPoseStack(), buffers, knife, now - knife.startTick - 1, camera);
            }
        }
        SLIVERS.draw(quads, now, camera);
        buffers.endBatch(GooRenderTypes.CRYSTAL_SHARD_TYPE);
        buffers.endBatch(GooSubmitter.renderType());
    }

    /**
     * Draws one knife an age into its flight, a tick behind the points it has
     * flown so it eases between them: flying, or sunk at its end while it
     * stands stuck.
     *
     * @param quads  the context the faces emit through
     * @param knife  the knife
     * @param age    ticks into its flight, with the partial tick
     * @param camera the camera's position
     */
    private static void drawKnife(FlatQuadContext quads, Knife knife, double age, Vec3 camera) {
        boolean stuck = knife.ending == GlassKnifePayload.Ending.STUCK && age >= knife.ticks();
        Placement placement = placementAt(knife.path, Math.max(0, age));
        Vec3 point = stuck ? placement.point().add(placement.heading().scale(SINK)) : placement.point();
        GlassKunai.emit(quads, point.subtract(camera), placement.heading(),
                (float) Math.min(Math.max(0, age), knife.ticks()) * ROLL_PER_TICK, 1f);
    }

    /**
     * Draws one of Dart's javelins an age into its flight as the dart form, in
     * its goo, point first along its path, held at its end once there.
     * decision knives-and-dart-function-as-projectiles
     *
     * @param poseStack the pose stack
     * @param buffers   the buffer source
     * @param knife     the javelin
     * @param age       ticks into its flight, with the partial tick
     * @param camera    the camera's position
     */
    private static void drawJavelin(PoseStack poseStack, MultiBufferSource buffers, Knife knife, double age,
                                    Vec3 camera) {
        Placement placement = placementAt(knife.path, Math.max(0, age));
        Vec3 at = placement.point().subtract(camera);
        poseStack.pushPose();
        poseStack.translate(at.x, at.y, at.z);
        GooFlightRenderer.emitTravelForm(poseStack.last(), buffers.getBuffer(GooSubmitter.renderType()),
                knife.javelin, TravelForm.DART, placement.heading(), 1f);
        poseStack.popPose();
    }

    /**
     * Where a knife's point is and where it heads an age into its flight:
     * between the tick points it passes, heading along the segment it is on,
     * held at its last point once there.
     *
     * @param path its tick points
     * @param age  ticks since it left the hand, with the partial tick
     * @return the point and unit heading
     */
    static Placement placementAt(List<Vec3> path, double age) {
        int last = path.size() - 1;
        if (last <= 0) {
            return new Placement(path.getFirst(), DOWN);
        }
        double clamped = Math.clamp(age, 0, last);
        int segment = Math.min((int) clamped, last - 1);
        Vec3 from = path.get(segment);
        Vec3 to = path.get(segment + 1);
        Vec3 along = to.subtract(from);
        Vec3 heading = along.lengthSqr() > 0 ? along.normalize() : DOWN;
        return new Placement(from.lerp(to, Mth.clamp(clamped - segment, 0, 1)), heading);
    }

    /**
     * A knife's point and heading at a moment.
     *
     * @param point   its point
     * @param heading its unit heading
     */
    record Placement(Vec3 point, Vec3 heading) {
    }
}
