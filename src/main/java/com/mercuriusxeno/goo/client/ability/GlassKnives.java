package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.crystal.KnifeHoming;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
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
 * Knives' glass knives in flight on this client: each leaves its fan slot as
 * the glass kunai it hovered as and homes on its mob tick by tick, point
 * first, rolling as it goes, turning toward where the mob stands on this
 * client. One meeting a block stands stuck there a moment, then shatters;
 * one reaching its mob shatters at once, and one whose mob is gone flies on
 * to where the mob last stood and shatters there, with a tinkle of glass,
 * cracking into splinters that tumble down and melt away.
 * decision knives-sling-then-morph-to-flechettes
 * decision knives-home-on-their-painted-mob
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

    /** One knife: the points it has passed each tick, its mob, how its flight ended, and when it left. */
    private static final class Knife {
        private final List<Vec3> path = new ArrayList<>();
        private final int mobId;
        private final long startTick;
        private Vec3 lastKnown;
        private GlassKnifePayload.@Nullable Ending ending;

        Knife(Vec3 start, int mobId, Vec3 lastKnown, long startTick) {
            path.add(start);
            this.mobId = mobId;
            this.lastKnown = lastKnown;
            this.startTick = startTick;
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
            if (gone) {
                shatter(level, path.getLast(), SoundEvents.GLASS_BREAK, SHATTER_VOLUME);
            }
            return gone;
        }

        private void fly(ClientLevel level) {
            Vec3 from = path.getLast();
            LivingEntity mob = level.getEntity(mobId) instanceof LivingEntity living && living.isAlive()
                    ? living : null;
            if (mob != null) {
                lastKnown = mob.getBoundingBox().getCenter();
            }
            Vec3 next = from.add(KnifeHoming.velocityToward(from, lastKnown));
            BlockHitResult wall = level.clip(new ClipContext(from, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, CollisionContext.empty()));
            boolean meetsAWall = wall.getType() == HitResult.Type.BLOCK;
            path.add(meetsAWall ? wall.getLocation() : next);
            endingOf(from, next, mob == null ? null : mob.getBoundingBox(), lastKnown, meetsAWall, ticks())
                    .ifPresent(ended -> {
                        ending = ended;
                        land(level, ended, path.getLast());
                    });
        }
    }

    /**
     * How a knife's step ends its flight: in a block it meets, on its mob
     * where the step reaches the mob's box, or spent where its mob is gone
     * and the step reached where the mob last stood or its flight ran out.
     *
     * @param from      where the step starts
     * @param next      where it stops
     * @param mob       the standing mob's box, or null where the mob is gone
     * @param lastKnown where the mob last stood
     * @param wall      whether the step meets a block
     * @param ticks     the ticks the knife has flown, this step included
     * @return how the flight ends, empty while it flies on
     */
    static Optional<GlassKnifePayload.Ending> endingOf(Vec3 from, Vec3 next, @Nullable AABB mob, Vec3 lastKnown,
                                                       boolean wall, int ticks) {
        if (wall) {
            return Optional.of(GlassKnifePayload.Ending.STUCK);
        }
        if (mob != null && KnifeHoming.arrives(from, next, mob)) {
            return Optional.of(GlassKnifePayload.Ending.STRUCK);
        }
        return spent(mob == null, next, lastKnown, ticks) ? Optional.of(GlassKnifePayload.Ending.SPENT)
                : Optional.empty();
    }

    // A knife whose mob is gone is spent where the mob last stood; any knife is spent once its flight runs out.
    private static boolean spent(boolean mobGone, Vec3 next, Vec3 lastKnown, int ticks) {
        return mobGone && next.equals(lastKnown) || ticks >= KnifeHoming.MOST_FLIGHT_TICKS;
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
                LIVE.add(new Knife(payload.start(), payload.mobId(), payload.lastKnown(), level.getGameTime()));
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
            drawKnife(quads, knife, now - knife.startTick - 1, camera);
        }
        SLIVERS.draw(quads, now, camera);
        buffers.endBatch(GooRenderTypes.CRYSTAL_SHARD_TYPE);
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
