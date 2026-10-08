package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.UnmakeStep;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Unmake's destabilizing waves: while right click holds Unmake, thin ripple
 * rings leave the glove fast and travel out, widening down the cone, with
 * clear gaps between them and no filled cone. They are staticky and chaotic:
 * each ring runs at its own speed, its radius jitters around its rim several
 * times a second, and its segments flicker and drop out like static. Each
 * ring is a thin band square to the look, its edges softened by
 * {@code unmake_waves.fsh}.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class UnmakeWaves {

    /** Rings in flight down the cone at once. */
    static final int RINGS = 7;
    /** Cone lengths a ring travels each tick, on average. */
    static final double RING_SPEED = 0.16;
    /** How far each ring's own speed strays from the average, as a share. */
    static final double SPEED_SPREAD = 0.35;
    /** How far a rim segment's radius jitters, as a share of the radius. */
    static final double RADIUS_JITTER = 0.14;
    /** Times a second the jitter and flicker pick anew, about eight. */
    static final double STATIC_RATE = 0.4;
    /** A ring's band width, in blocks. */
    static final double RING_WIDTH = 0.07;
    /** Where a ring leaves the glove, in blocks past it. */
    static final double FROM_THE_GLOVE = 0.05;
    private static final int SEGMENTS = 48;
    private static final double TWO_PI = 2 * Math.PI;
    /** Stretches a 0 to 1 share across -1 to 1. */
    private static final double SIGNED_SPAN = 2;
    private static final double HALF = 0.5;
    private static final int OPAQUE = 0xFF;
    /** A look this close to straight up or down crosses x rather than up to find its sides. */
    private static final double NEAR_VERTICAL = 0.99;
    /** Seeds apart from one ring to the next, so no two rings share their static. */
    private static final long SEED_PER_RING = 7919;
    /** Seeds the flicker apart from the jitter. */
    private static final long FLICKER_SALT = 104_729;
    private static final long NOISE_MULTIPLIER = 6_364_136_223_846_793_005L;
    private static final long NOISE_INCREMENT = 1_442_695_040_888_963_407L;
    private static final int NOISE_SHIFT = 33;
    private static final long NOISE_MASK = 0xFFFF;

    private UnmakeWaves() {
    }

    /**
     * Draws the waves after the translucent blocks while Unmake is held.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientAbility unmake = player == null ? null : heldUnmake(player);
        if (unmake == null) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        Vec3 apex = GloveAim.handPosition(mc.gameRenderer.getMainCamera()).subtract(camera);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        FlatQuadContext quads = new FlatQuadContext(event.getPoseStack().last(),
                buffers.getBuffer(GooRenderTypes.UNMAKE_WAVES_TYPE));
        emitRings(quads, unmake, apex, player.getViewVector(partialTick), player.level().getGameTime() + partialTick);
        buffers.endBatch(GooRenderTypes.UNMAKE_WAVES_TYPE);
    }

    /**
     * Emits every ring in flight this frame.
     *
     * @param quads  the quad emitter
     * @param unmake the held Unmake, whose delivery shapes the cone
     * @param apex   the glove, camera relative
     * @param axis   the look's unit vector
     * @param ticks  the game time including the partial tick
     */
    private static void emitRings(FlatQuadContext quads, ClientAbility unmake, Vec3 apex, Vec3 axis, double ticks) {
        double tan = Math.tan(Math.toRadians(unmake.delivery().coneDegrees() * HALF));
        double range = unmake.delivery().range();
        int frame = (int) Math.floor(ticks * STATIC_RATE);
        for (int ring = 0; ring < RINGS; ring++) {
            double along = ringAlong(ring, ticks);
            double distance = FROM_THE_GLOVE + (range - FROM_THE_GLOVE) * along;
            emitRing(quads, new Ring(apex.add(axis.scale(distance)), axis, distance * tan, (float) along,
                    ring * SEED_PER_RING + frame));
        }
    }

    /**
     * How far down the cone a ring stands, the rings spaced evenly and all
     * travelling outward, each leaving the glove again once it reaches the end.
     *
     * @param ring  the ring's index
     * @param ticks the game time including the partial tick
     * @return the share of the cone's length, 0 at the glove to 1 at its reach
     */
    static double ringAlong(int ring, double ticks) {
        double speed = RING_SPEED * (1 + SPEED_SPREAD * signedNoise(ring * SEED_PER_RING));
        double phase = ticks * speed + (double) ring / RINGS;
        return phase - Math.floor(phase);
    }

    /**
     * A repeatable random swing for a seed, from -1 to 1.
     *
     * @param seed the seed
     * @return the swing
     */
    static double signedNoise(long seed) {
        return noise(seed) * SIGNED_SPAN - 1;
    }

    /**
     * A repeatable random share for a seed, the static's source.
     *
     * @param seed the seed
     * @return a share from 0 to 1
     */
    static double noise(long seed) {
        long mixed = seed * NOISE_MULTIPLIER + NOISE_INCREMENT;
        mixed ^= mixed >>> NOISE_SHIFT;
        return (mixed & NOISE_MASK) / (double) NOISE_MASK;
    }

    /**
     * One ring this frame.
     *
     * @param center the ring's center, camera relative
     * @param axis   the look's unit vector
     * @param radius the ring's radius
     * @param along  how far down the cone it stands, 0 to 1
     * @param seed   the seed its static is picked from this frame
     */
    private record Ring(Vec3 center, Vec3 axis, double radius, float along, long seed) {
    }

    /**
     * Emits one ring: a thin band square to the look, its radius jittering
     * from segment to segment and each segment flickering. Each vertex carries
     * how far down the cone the ring stands in red, which edge of the band it
     * sits on in green, 0 inner to 1 outer, and its segment's flicker in blue.
     *
     * @param quads the quad emitter
     * @param ring  the ring
     */
    private static void emitRing(FlatQuadContext quads, Ring ring) {
        Vec3 axis = ring.axis();
        Vec3 side = axis.cross(Math.abs(axis.y) < NEAR_VERTICAL ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 up = side.cross(axis);
        int red = Math.round(ring.along() * OPAQUE);
        double[] radii = new double[SEGMENTS + 1];
        for (int corner = 0; corner < SEGMENTS; corner++) {
            radii[corner] = ring.radius() * (1 + RADIUS_JITTER * signedNoise(ring.seed() * SEGMENTS + corner));
        }
        radii[SEGMENTS] = radii[0];
        for (int segment = 0; segment < SEGMENTS; segment++) {
            int flicker = (int) Math.round(noise(ring.seed() * SEGMENTS + segment + FLICKER_SALT) * OPAQUE);
            int innerColor = ARGB.color(OPAQUE, red, 0, flicker);
            int outerColor = ARGB.color(OPAQUE, red, OPAQUE, flicker);
            double a0 = TWO_PI * segment / SEGMENTS;
            double a1 = TWO_PI * (segment + 1) / SEGMENTS;
            vertex(quads, onRing(ring.center(), side, up, radii[segment] - RING_WIDTH * HALF, a0), axis, innerColor);
            vertex(quads, onRing(ring.center(), side, up, radii[segment] + RING_WIDTH * HALF, a0), axis, outerColor);
            vertex(quads, onRing(ring.center(), side, up, radii[segment + 1] + RING_WIDTH * HALF, a1), axis,
                    outerColor);
            vertex(quads, onRing(ring.center(), side, up, radii[segment + 1] - RING_WIDTH * HALF, a1), axis,
                    innerColor);
        }
    }

    private static Vec3 onRing(Vec3 center, Vec3 side, Vec3 up, double radius, double angle) {
        double clamped = Math.max(0, radius);
        return center.add(side.scale(Math.cos(angle) * clamped)).add(up.scale(Math.sin(angle) * clamped));
    }

    private static void vertex(FlatQuadContext quads, Vec3 at, Vec3 axis, int color) {
        quads.vertex((float) at.x, (float) at.y, (float) at.z, color, (float) axis.x, (float) axis.y,
                (float) axis.z);
    }

    /**
     * The Unmake the local player's glove holds while right click holds it.
     *
     * @param player the local player
     * @return the ability, or null while no Unmake is held
     */
    static @Nullable ClientAbility heldUnmake(LocalPlayer player) {
        String abilityId = GloveAim.selectedAbilityId(player);
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean unmakes = ability != null && ability.behaviors().stream().anyMatch(UnmakeStep.class::isInstance);
        return unmakes && GloveUseTracker.showsArea() ? ability : null;
    }
}
