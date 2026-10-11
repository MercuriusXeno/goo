package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.crystal.KnifeRain;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.network.GlassKnifePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Shards' glass knives in flight on this client: each kunai flies its
 * server's arc point first, rolling as it goes, and ends as the server
 * found: one stuck in a block stands there a moment, then shatters; one
 * striking a mob, or spent in the air, shatters at once, with a tinkle of
 * glass, cracking into splinters that tumble down and melt away.
 * decision shards-sling-then-morph-to-flechettes
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

    /**
     * One knife: the points it passes each tick, how it ends, and when.
     *
     * @param path      its point each tick of its flight, the last where it ends
     * @param ending    how it ends
     * @param startTick the game time it left the hand
     * @param landed    whether it has landed and made its sound
     */
    private record Knife(List<Vec3> path, GlassKnifePayload.Ending ending, long startTick, boolean[] landed) {

        int ticks() {
            return path.size() - 1;
        }
    }

    /**
     * The points a knife passes each tick of its flight, ending where the
     * server found it ends.
     *
     * @param start    where it leaves the hand
     * @param velocity its velocity leaving the hand
     * @param ticks    the ticks it flies
     * @param end      where it ends
     * @return ticks plus one points, the first the start and the last the end
     */
    static List<Vec3> pathOf(Vec3 start, Vec3 velocity, int ticks, Vec3 end) {
        List<Vec3> path = new ArrayList<>(ticks + 1);
        Vec3 position = start;
        Vec3 moving = velocity;
        for (int tick = 0; tick < ticks; tick++) {
            path.add(position);
            position = position.add(moving);
            moving = KnifeRain.nextVelocity(moving);
        }
        path.add(end);
        return path;
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
                LIVE.add(new Knife(pathOf(payload.start(), payload.velocity(), payload.ticks(), payload.end()),
                        GlassKnifePayload.Ending.values()[payload.ending()], level.getGameTime(), new boolean[1]));
            }
        });
    }

    /** Drops every knife, as a disconnect does. */
    public static void clear() {
        LIVE.clear();
        SLIVERS.clear();
    }

    /**
     * Client tick: a knife reaching its end sounds and, unless it sticks,
     * shatters; a stuck knife shatters once it has stood its while.
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
        LIVE.removeIf(knife -> tickKnife(level, knife, now - knife.startTick()));
    }

    /**
     * Lands a knife reaching its end, and shatters a stuck one that has
     * stood its while.
     *
     * @param level the client level
     * @param knife the knife
     * @param age   ticks since it left the hand
     * @return true once the knife is gone
     */
    private static boolean tickKnife(ClientLevel level, Knife knife, long age) {
        if (age >= knife.ticks() && !knife.landed()[0]) {
            knife.landed()[0] = true;
            land(level, knife);
        }
        if (knife.ending() != GlassKnifePayload.Ending.STUCK) {
            return age >= knife.ticks();
        }
        boolean gone = age >= knife.ticks() + STUCK_TICKS;
        if (gone) {
            shatter(level, knife.path().getLast(), SoundEvents.GLASS_BREAK, SHATTER_VOLUME);
        }
        return gone;
    }

    private static void land(ClientLevel level, Knife knife) {
        Vec3 end = knife.path().getLast();
        switch (knife.ending()) {
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
            drawKnife(quads, knife, now - knife.startTick(), camera);
        }
        SLIVERS.draw(quads, now, camera);
        buffers.endBatch(GooRenderTypes.CRYSTAL_SHARD_TYPE);
    }

    /**
     * Draws one knife an age into its flight: flying, or sunk at its end
     * while it stands stuck; a knife that shattered draws nothing.
     *
     * @param quads  the context the faces emit through
     * @param knife  the knife
     * @param age    ticks since it left the hand, with the partial tick
     * @param camera the camera's position
     */
    private static void drawKnife(FlatQuadContext quads, Knife knife, double age, Vec3 camera) {
        boolean ended = age >= knife.ticks();
        boolean stuck = knife.ending() == GlassKnifePayload.Ending.STUCK;
        if (age < 0 || ended && !stuck) {
            return;
        }
        Placement placement = placementAt(knife.path(), age);
        Vec3 point = ended ? placement.point().add(placement.heading().scale(SINK)) : placement.point();
        GlassKunai.emit(quads, point.subtract(camera), placement.heading(),
                (float) Math.min(age, knife.ticks()) * ROLL_PER_TICK, 1f);
    }

    /**
     * Where a knife's point is and where it heads an age into its flight:
     * between the tick points it passes, heading along the segment it is on,
     * held at its end once there.
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
