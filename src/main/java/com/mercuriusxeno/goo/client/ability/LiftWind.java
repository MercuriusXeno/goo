package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.LineContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Lift's wind: frost's wind lines, white and pale gray, leave the prism's
 * base close around it and spiral out around it as they climb, widening turn
 * by turn, then carry on up the shaft above it and fade.
 * lift-prism-levitates-the-block-above
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LiftWind {

    /** Ticks a line lives, from the prism's base to fading out up the shaft. */
    static final int LIFE_TICKS = 40;
    /** Ticks between lines leaving a lift's base. */
    static final int EVERY_TICKS = 3;
    /** How far from the prism's middle a line leaves, in blocks: close around the crystal. */
    static final double START_RADIUS = 0.3;
    /** How far from the middle a line has spiralled out to when it fades, in blocks. */
    static final double END_RADIUS = 0.9;
    /** Turns a line winds around the prism over its life. */
    static final double TURNS = 1.5;
    /** Ticks of the path the trailing line spans behind its head. */
    static final double TAIL_TICKS = 10;
    private static final int TAIL_SAMPLES = 12;
    /** The share of its life a line fades in over, and out over. */
    private static final double FADE_SHARE = 0.2;
    private static final int MAX_ALPHA = 200;
    private static final int WHITE = 0xF4F8FF;
    private static final int PALE_GRAY = 0xD6DDE8;
    private static final float LINE_WIDTH = 3f;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double CLOCKWISE = 1;
    private static final double COUNTERCLOCKWISE = -1;

    /**
     * One spiralling line.
     *
     * @param base       the middle of the prism's base
     * @param height     how far it climbs over its life, the prism and the shaft above it
     * @param startAngle where around the prism it leaves, in radians
     * @param winding    which way it winds, 1 or -1
     * @param gray       how far its color leans from white to pale gray, 0 to 1
     * @param startTick  the game time it left
     */
    record Spiral(Vec3 base, double height, double startAngle, double winding, float gray, long startTick) {
    }

    private static final List<Spiral> LIVE = new ArrayList<>();

    private LiftWind() {
    }

    /**
     * Sends one line spiralling up from a lift's base on a leaving tick.
     *
     * @param prism    the prism's block
     * @param shaft    the shaft's height above the prism, in blocks
     * @param random   the random source
     * @param gameTime the game time
     */
    static void blow(BlockPos prism, int shaft, RandomSource random, long gameTime) {
        if (gameTime % EVERY_TICKS == 0) {
            LIVE.add(new Spiral(Vec3.atBottomCenterOf(prism), 1 + shaft, random.nextDouble() * TWO_PI,
                    random.nextBoolean() ? CLOCKWISE : COUNTERCLOCKWISE, random.nextFloat(), gameTime));
        }
    }

    /**
     * Where a line's head stands a number of ticks after it left: turned
     * around the prism and widened out from it by the share of its life
     * lived, and risen as far.
     *
     * @param spiral the line
     * @param age    ticks since it left
     * @return the head's world position
     */
    static Vec3 spiralPoint(Spiral spiral, double age) {
        double life = Math.clamp(age / LIFE_TICKS, 0, 1);
        double angle = spiral.startAngle() + spiral.winding() * TURNS * TWO_PI * life;
        double radius = START_RADIUS + (END_RADIUS - START_RADIUS) * life;
        return spiral.base().add(Math.cos(angle) * radius, spiral.height() * life, Math.sin(angle) * radius);
    }

    /**
     * A line's opacity over its life: fading in at the base, holding, then
     * fading out up the shaft.
     *
     * @param life the share of its life lived, 0 to 1
     * @return the opacity, 0 to 1
     */
    static double opacity(double life) {
        return Math.clamp(Math.min(life, 1 - life) / FADE_SHARE, 0, 1);
    }

    /** Drops every line, as a disconnect does. */
    public static void clear() {
        LIVE.clear();
    }

    /**
     * Drops the spent lines and draws the rest.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LIVE.clear();
            return;
        }
        long now = mc.level.getGameTime();
        LIVE.removeIf(spiral -> now - spiral.startTick() >= LIFE_TICKS);
        if (LIVE.isEmpty()) {
            return;
        }
        double gameTime = now + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderTypes.linesTranslucent();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(type));
        for (Spiral spiral : LIVE) {
            drawSpiral(lines, camera, spiral, gameTime - spiral.startTick());
        }
        buffers.endBatch(type);
    }

    private static void drawSpiral(LineContext lines, Vec3 camera, Spiral spiral, double age) {
        double alpha = MAX_ALPHA * opacity(age / LIFE_TICKS);
        int rgb = ARGB.srgbLerp(spiral.gray(), WHITE, PALE_GRAY);
        Vec3 previous = spiralPoint(spiral, age);
        for (int sample = 1; sample <= TAIL_SAMPLES; sample++) {
            double tail = (double) sample / TAIL_SAMPLES;
            Vec3 next = spiralPoint(spiral, Math.max(0, age - tail * TAIL_TICKS));
            lines.emitPolyline(camera, new Vec3[] {previous, next}, ARGB.color((int) Math.round(alpha * (1 - tail)), rgb),
                    LINE_WIDTH);
            previous = next;
        }
    }
}
