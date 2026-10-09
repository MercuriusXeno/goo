package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The swirling frost nova an Orb carries: frost's fog lies flat about the
 * ball out to the swirl's reach, and over it curling arms of frost-white
 * wind turn about the ball, brightest at the core and fading toward the
 * rim, shedding snowflakes as they spin, so the nova reads as a slow storm
 * rolling with the ball rather than a ring that only expands.
 * orb-carries-a-swirling-nova
 */
public final class OrbSwirl {

    /** The curling arms about the ball. */
    static final int ARMS = 3;
    /** Radians the swirl turns each tick. */
    static final double TURN_PER_TICK = 0.25;
    /** Radians an arm curls back across its length, from core to rim. */
    static final double CURL = 2.4;
    /** Where an arm starts, as a share of the swirl's reach. */
    static final double CORE_SHARE = 0.15;
    private static final int ARM_SAMPLES = 10;
    /** How far an arm rises and falls about the ball's height, in blocks, and how fast. */
    private static final double BOB = 0.15;
    private static final double BOB_PER_TICK = 0.2;
    private static final float LINE_WIDTH = 3f;
    private static final int MAX_ALPHA = 210;
    private static final int FROST_WHITE = 0xEAF6FF;
    /** The progress at which frost's fog ring stands fully spread and whole. */
    private static final float FOG_SPREAD_WHOLE = (float) FrostExplosionVisual.SPREAD_TICKS
            / FrostExplosionVisual.DURATION_TICKS;
    /** The chance each arm sheds a snowflake on a frame. */
    private static final float SNOWFLAKE_CHANCE = 0.08f;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF_BLOCK = 0.5;

    private OrbSwirl() {
    }

    /**
     * A point on one of the swirl's arms: the arm starts near the core at
     * its turn of the swirl and curls back as it reaches out toward the rim.
     *
     * @param center the ball
     * @param reach  the swirl's reach in blocks
     * @param arm    which arm, 0 to ARMS - 1
     * @param along  the share of the way from core to rim, 0 to 1
     * @param time   the game time in ticks
     * @return the point
     */
    static Vec3 armPoint(Vec3 center, double reach, int arm, double along, double time) {
        double radius = reach * (CORE_SHARE + (1 - CORE_SHARE) * along);
        double angle = TWO_PI * arm / ARMS + time * TURN_PER_TICK - along * CURL;
        double bob = BOB * Math.sin(time * BOB_PER_TICK + arm + along * Math.PI);
        return center.add(radius * Math.cos(angle), bob, radius * Math.sin(angle));
    }

    /**
     * Draws the swirl about a ball in flight and sheds its snowflakes.
     *
     * @param level     the client level
     * @param poseStack the level's pose stack, camera relative
     * @param buffers   the buffer source the frame draws into
     * @param camera    the camera's world position
     * @param center    the ball
     * @param reach     the swirl's reach in blocks
     * @param time      the game time including the partial tick
     */
    public static void draw(ClientLevel level, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                            Vec3 camera, Vec3 center, float reach, float time) {
        FrostExplosionVisual.drawRing(new BurnoutFrame(poseStack, buffers, camera, time),
                center.subtract(HALF_BLOCK, HALF_BLOCK, HALF_BLOCK), Direction.UP, 0f, reach, FOG_SPREAD_WHOLE);
        RenderType type = RenderTypes.linesTranslucent();
        LineContext lines = new LineContext(poseStack.last(), buffers.getBuffer(type));
        RandomSource random = level.getRandom();
        for (int arm = 0; arm < ARMS; arm++) {
            Vec3 previous = armPoint(center, reach, arm, 0, time);
            for (int sample = 1; sample <= ARM_SAMPLES; sample++) {
                double along = (double) sample / ARM_SAMPLES;
                Vec3 next = armPoint(center, reach, arm, along, time);
                lines.emitPolyline(camera, new Vec3[] {previous, next}, armColor(along), LINE_WIDTH);
                previous = next;
            }
            if (random.nextFloat() < SNOWFLAKE_CHANCE) {
                level.addParticle(GooParticles.SNOWFLAKE.get(), previous.x, previous.y, previous.z, 0, 0, 0);
            }
        }
        buffers.endBatch(type);
    }

    /**
     * An arm's color at a share of its length: frost white, fading out toward the rim.
     *
     * @param along the share of the way from core to rim, 0 to 1
     * @return the ARGB color
     */
    static int armColor(double along) {
        return ARGB.color((int) Math.round((1 - along) * MAX_ALPHA), FROST_WHITE);
    }
}
