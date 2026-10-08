package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.SiphonStep;
import com.mercuriusxeno.goo.ability.program.SoupBall;
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
 * rings leave the glove and travel out, widening down the cone, with clear
 * gaps between them and no filled cone. Each ring is a thin band square to
 * the look, its edges softened by {@code unmake_waves.fsh}.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class UnmakeWaves {

    /** Rings in flight down the cone at once. */
    static final int RINGS = 4;
    /** The rings' cone, out from the glove to the soup ball, in degrees. */
    static final double CONE_DEGREES = 40;
    /** Cone lengths a ring travels each tick. */
    static final double RING_SPEED = 0.12;
    /** A ring's band width, in blocks. */
    static final double RING_WIDTH = 0.07;
    /** Where a ring leaves the glove, in blocks past it. */
    static final double FROM_THE_GLOVE = 0.05;
    private static final int SEGMENTS = 48;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;
    private static final int OPAQUE = 0xFF;
    /** A look this close to straight up or down crosses x rather than up to find its sides. */
    private static final double NEAR_VERTICAL = 0.99;
    /** Lobes in each ring's smooth warp. */
    static final int WARP_LOBES = 3;
    /** How far the warp swings a ring's radius, as a share of it. */
    static final double WARP_DEPTH = 0.1;
    /** Turns of the warp about the ring each tick. */
    static final double WARP_SPIN = 0.02;
    /** Arcs skittering along each ring. */
    static final int ARCS = 2;
    /** How much of the ring one arc spans, as a share of a turn. */
    static final double ARC_SPAN = 0.12;
    /** Kinks along one arc. */
    static final int ARC_KINKS = 8;
    /** How far an arc's kinks stray from the ring, in blocks. */
    static final double ARC_STRAY = 0.06;
    /** An arc's width, in blocks, thinner than the ring. */
    static final double ARC_WIDTH = 0.025;
    /** Times a second the arcs re-fork, about five. */
    static final double ARC_RATE = 0.25;
    private static final long SEED_PER_RING = 7919;
    private static final long NOISE_MULTIPLIER = 6_364_136_223_846_793_005L;
    private static final long NOISE_INCREMENT = 1_442_695_040_888_963_407L;
    private static final int NOISE_SHIFT = 33;
    private static final long NOISE_MASK = 0xFFFF;
    private static final double SIGNED_SPAN = 2;

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
        Vec3 axis = player.getViewVector(partialTick);
        double ticks = player.level().getGameTime() + partialTick;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        emitRings(new FlatQuadContext(event.getPoseStack().last(), buffers.getBuffer(GooRenderTypes.UNMAKE_WAVES_TYPE)),
                apex, axis, ticks);
        buffers.endBatch(GooRenderTypes.UNMAKE_WAVES_TYPE);
    }

    /**
     * Emits the rings, each travelling out from the glove to the soup ball.
     *
     * @param quads the quad emitter
     * @param apex  the glove, camera relative
     * @param axis  the look's unit vector
     * @param ticks the game time including the partial tick
     */
    private static void emitRings(FlatQuadContext quads, Vec3 apex, Vec3 axis, double ticks) {
        double tan = Math.tan(Math.toRadians(CONE_DEGREES * HALF));
        double range = SoupBall.DISTANCE;
        for (int ring = 0; ring < RINGS; ring++) {
            double along = ringAlong(ring, ticks);
            double distance = FROM_THE_GLOVE + (range - FROM_THE_GLOVE) * along;
            Vec3 center = apex.add(axis.scale(distance));
            emitRing(quads, center, axis, distance * tan, (float) along, warpPhase(ring, ticks));
            emitArcs(quads, center, axis, distance * tan, (float) along,
                    ring * SEED_PER_RING + (long) Math.floor(ticks * ARC_RATE));
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
        double phase = ticks * RING_SPEED + (double) ring / RINGS;
        return phase - Math.floor(phase);
    }

    /**
     * Where a ring's smooth warp stands this tick: each ring warps and spins
     * on its own phase.
     *
     * @param ring  the ring's index
     * @param ticks the game time including the partial tick
     * @return the warp's phase, in turns
     */
    static double warpPhase(int ring, double ticks) {
        return ticks * WARP_SPIN + (double) ring / RINGS;
    }

    /**
     * A ring's radius at an angle: the ring warped in a slow, smooth wave of
     * a few lobes, never jagged.
     *
     * @param radius the ring's radius
     * @param angle  the angle about the ring
     * @param phase  the warp's phase, in turns
     * @return the warped radius
     */
    static double warpedRadius(double radius, double angle, double phase) {
        return radius * (1 + WARP_DEPTH * Math.sin(WARP_LOBES * angle + phase * TWO_PI));
    }

    /**
     * Emits one ring: a thin band square to the look, warped smoothly about
     * its rim. Each vertex carries how far down the cone the ring stands in
     * red, and which edge of the band it sits on in green, 0 inner to 1 outer.
     *
     * @param quads  the quad emitter
     * @param center the ring's center, camera relative
     * @param axis   the look's unit vector
     * @param radius the ring's radius
     * @param along  how far down the cone it stands, 0 to 1
     * @param phase  the warp's phase, in turns
     */
    private static void emitRing(FlatQuadContext quads, Vec3 center, Vec3 axis, double radius, float along,
                                 double phase) {
        Vec3 side = sideOf(axis);
        Vec3 up = side.cross(axis);
        int innerColor = ARGB.color(OPAQUE, Math.round(along * OPAQUE), 0, 0);
        int outerColor = ARGB.color(OPAQUE, Math.round(along * OPAQUE), OPAQUE, 0);
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double a0 = TWO_PI * segment / SEGMENTS;
            double a1 = TWO_PI * (segment + 1) / SEGMENTS;
            double r0 = warpedRadius(radius, a0, phase);
            double r1 = warpedRadius(radius, a1, phase);
            vertex(quads, onRing(center, side, up, Math.max(0, r0 - RING_WIDTH * HALF), a0), axis, innerColor);
            vertex(quads, onRing(center, side, up, r0 + RING_WIDTH * HALF, a0), axis, outerColor);
            vertex(quads, onRing(center, side, up, r1 + RING_WIDTH * HALF, a1), axis, outerColor);
            vertex(quads, onRing(center, side, up, Math.max(0, r1 - RING_WIDTH * HALF), a1), axis, innerColor);
        }
    }

    /**
     * Emits a ring's arcs: thin bright lines of goo-green lightning
     * skittering along it, each forking along a stretch of the rim, its
     * kinks straying from the ring and re-picked a few times a second.
     *
     * @param quads  the quad emitter
     * @param center the ring's center, camera relative
     * @param axis   the look's unit vector
     * @param radius the ring's radius
     * @param along  how far down the cone it stands, 0 to 1
     * @param seed   the seed the arcs fork from this moment
     */
    private static void emitArcs(FlatQuadContext quads, Vec3 center, Vec3 axis, double radius, float along,
                                 long seed) {
        Vec3 side = sideOf(axis);
        Vec3 up = side.cross(axis);
        int innerColor = ARGB.color(OPAQUE, Math.round(along * OPAQUE), 0, 0);
        int outerColor = ARGB.color(OPAQUE, Math.round(along * OPAQUE), OPAQUE, 0);
        for (int arc = 0; arc < ARCS; arc++) {
            long arcSeed = seed * ARCS + arc;
            double start = TWO_PI * noise(arcSeed);
            double[] strays = new double[ARC_KINKS + 1];
            for (int kink = 1; kink < ARC_KINKS; kink++) {
                strays[kink] = ARC_STRAY * signedNoise(arcSeed * ARC_KINKS + kink);
            }
            for (int kink = 0; kink < ARC_KINKS; kink++) {
                double a0 = start + TWO_PI * ARC_SPAN * kink / ARC_KINKS;
                double a1 = start + TWO_PI * ARC_SPAN * (kink + 1) / ARC_KINKS;
                double r0 = radius + strays[kink];
                double r1 = radius + strays[kink + 1];
                vertex(quads, onRing(center, side, up, Math.max(0, r0 - ARC_WIDTH * HALF), a0), axis, innerColor);
                vertex(quads, onRing(center, side, up, r0 + ARC_WIDTH * HALF, a0), axis, outerColor);
                vertex(quads, onRing(center, side, up, r1 + ARC_WIDTH * HALF, a1), axis, outerColor);
                vertex(quads, onRing(center, side, up, Math.max(0, r1 - ARC_WIDTH * HALF), a1), axis, innerColor);
            }
        }
    }

    /**
     * @param axis the look's unit vector
     * @return a unit direction square to it, across the ring
     */
    private static Vec3 sideOf(Vec3 axis) {
        return axis.cross(Math.abs(axis.y) < NEAR_VERTICAL ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
    }

    /**
     * A repeatable random share for a seed, which forks the arcs.
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
     * @param seed the seed
     * @return a repeatable random swing from -1 to 1
     */
    static double signedNoise(long seed) {
        return noise(seed) * SIGNED_SPAN - 1;
    }

    private static Vec3 onRing(Vec3 center, Vec3 side, Vec3 up, double radius, double angle) {
        return center.add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
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
        boolean unmakes = ability != null && ability.behaviors().stream().anyMatch(SiphonStep.class::isInstance);
        return unmakes && GloveUseTracker.showsArea() ? ability : null;
    }
}
