package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.ability.WispBlock;
import com.mercuriusxeno.goo.block.ability.WispBlockEntity;
import com.mercuriusxeno.goo.client.ability.WispGlow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws a wisp as a soft floating mote: a small near-white cube inside a
 * glow-yellow halo cube, both added onto the world, bobbing and turning
 * slowly in place, each wisp at its own phase and pace so no two move in
 * step, and shrinking and dimming through the wisp's fade stages (operator
 * rulings 2026-10-09). A fresh wisp grows in from nothing where it stands.
 * The wisp queues to {@link WispGlow}, which draws it after the translucent blocks so water
 * shows behind it, not over it.
 * decision radiant-wisps-where-light-is-low
 * operator ruling 2026-10-10: a wisp fades in rather than popping in
 */
public class WispRenderer implements BlockEntityRenderer<WispBlockEntity, WispRenderer.WispRenderState> {

    /** The core cube's half-size, in blocks. */
    static final float CORE_HALF = 0.06f;
    /** The halo cube's half-size, in blocks. */
    static final float HALO_HALF = 0.15f;
    private static final int CORE_ALPHA = 210;
    private static final int HALO_ALPHA = 55;
    private static final int CORE_RGB = 0xFFF6C8;
    private static final int HALO_RGB = 0xFFE628;
    private static final float BOB_HEIGHT = 0.06f;
    /** Ticks a fresh wisp takes to fade in from nothing, half a second. */
    static final int FADE_IN_TICKS = 10;
    /** Smoothstep's terms, 3s² - 2s³, so the fade-in starts and ends gently. */
    private static final float SMOOTHSTEP_RISE = 3f;
    private static final float SMOOTHSTEP_EASE = 2f;
    /** The slowest a wisp bobs, in radians a tick, and how much faster one may go. */
    static final float SLOWEST_BOB = 0.05f;
    static final float BOB_SPREAD = 0.05f;
    /** Degrees a wisp turns each tick, at most, either way. */
    private static final float MOST_TURN = 1.5f;
    private static final int PHASE_BITS = 0xFFFF;
    private static final int PACE_SHIFT = 16;
    private static final int TURN_SHIFT = 32;
    private static final float PHASE_STEPS = 65_536f;
    /** The second wobble's pace over the first's: irrational, so the two never fall into step. */
    private static final float GOLDEN_RATIO = 1.618034f;
    /** The slow bob's share of the float, the wobble taking the rest. */
    private static final float SLOW_SHARE = 0.65f;
    private static final int WOBBLE_SHIFT = 48;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX_MULTIPLIER_A = 0xBF58476D1CE4E5B9L;
    private static final long MIX_MULTIPLIER_B = 0x94D049BB133111EBL;
    private static final int MIX_SHIFT_A = 30;
    private static final int MIX_SHIFT_B = 27;
    private static final int MIX_SHIFT_C = 31;
    /** A turn spans its most on both sides. */
    private static final float BOTH_WAYS = 2f;
    /** A cube corner's coordinate: the low side or the high side of the unit cube. */
    private static final float LO = -1f;
    private static final float HI = 1f;
    /** A corner takes three coordinates, in x, y, z order. */
    private static final int AXES = 3;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    /** The unit cube's six faces, each four corners of x, y and z. */
    private static final float[][] CUBE_FACES = {
        {LO, LO, LO, HI, LO, LO, HI, LO, HI, LO, LO, HI}, {LO, HI, LO, LO, HI, HI, HI, HI, HI, HI, HI, LO},
        {LO, LO, LO, LO, HI, LO, HI, HI, LO, HI, LO, LO}, {LO, LO, HI, HI, LO, HI, HI, HI, HI, LO, HI, HI},
        {LO, LO, LO, LO, LO, HI, LO, HI, HI, LO, HI, LO}, {HI, LO, LO, HI, HI, LO, HI, HI, HI, HI, LO, HI}};

    /**
     * Creates the wisp renderer.
     *
     * @param context the renderer context
     */
    public WispRenderer(BlockEntityRendererProvider.Context context) {
        // The mote is procedural; the context carries nothing it draws from.
    }

    @Override
    public WispRenderState createRenderState() {
        return new WispRenderState();
    }

    @Override
    public void extractRenderState(WispBlockEntity wisp, WispRenderState state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(wisp, state, breakProgress);
        state.fade = wisp.getBlockState().getValue(WispBlock.FADE);
        long gameTime = wisp.getLevel() == null ? 0L : wisp.getLevel().getGameTime();
        state.time = gameTime + partialTick;
        state.seed = seedOf(wisp.getBlockPos().asLong());
        state.age = wisp.appearedAt() == WispBlockEntity.NOT_YET ? FADE_IN_TICKS : state.time - wisp.appearedAt();
    }

    @Override
    public void submit(WispRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState cameraState) {
        float strength = strength(state.fade) * fadeIn(state.age);
        Vec3 center = Vec3.atCenterOf(state.blockPos).add(0, bob(state.seed, state.time), 0);
        // operator UAT 2026-10-10: water drew over the wisps, so they draw after the translucent blocks
        WispGlow.queue(new WispGlow.Sprite(center, state.time * turn(state.seed), (pose, consumer) -> {
            emitCube(pose, consumer, HALO_HALF * strength, ARGB.color(Math.round(HALO_ALPHA * strength), HALO_RGB));
            emitCube(pose, consumer, CORE_HALF * strength, ARGB.color(Math.round(CORE_ALPHA * strength), CORE_RGB));
        }));
    }

    /**
     * Scrambles a packed position into a seed whose bits spread well, so
     * neighboring wisps take unrelated phases and paces.
     *
     * @param packed the wisp's packed position
     * @return the seed
     */
    static long seedOf(long packed) {
        long z = packed * GOLDEN_GAMMA;
        z = (z ^ (z >>> MIX_SHIFT_A)) * MIX_MULTIPLIER_A;
        z = (z ^ (z >>> MIX_SHIFT_B)) * MIX_MULTIPLIER_B;
        return z ^ (z >>> MIX_SHIFT_C);
    }

    /**
     * How far a wisp has bobbed at a time: two wobbles at its own phases and
     * at paces in an irrational ratio, so its float never settles into a
     * rhythm, and no two wisps float in step.
     *
     * @param seed the wisp's position seed
     * @param time the game clock in ticks
     * @return the bob's height off center, in blocks
     */
    static float bob(long seed, float time) {
        float slow = Mth.sin(phase(seed) + time * pace(seed));
        float wobble = Mth.sin(phase(seed >>> WOBBLE_SHIFT) + time * pace(seed) * GOLDEN_RATIO);
        return BOB_HEIGHT * (SLOW_SHARE * slow + (1f - SLOW_SHARE) * wobble);
    }

    /**
     * A wisp's bob phase, from its position's seed, anywhere in a full turn.
     *
     * @param seed the wisp's position seed
     * @return the phase in radians
     */
    static float phase(long seed) {
        return (seed & PHASE_BITS) / PHASE_STEPS * Mth.TWO_PI;
    }

    /**
     * A wisp's bob pace, from its position's seed, between the slowest and the fastest.
     *
     * @param seed the wisp's position seed
     * @return radians a tick
     */
    static float pace(long seed) {
        return SLOWEST_BOB + ((seed >>> PACE_SHIFT) & PHASE_BITS) / PHASE_STEPS * BOB_SPREAD;
    }

    /**
     * A wisp's turn, from its position's seed, slow either way.
     *
     * @param seed the wisp's position seed
     * @return degrees a tick
     */
    static float turn(long seed) {
        return (((seed >>> TURN_SHIFT) & PHASE_BITS) / PHASE_STEPS * BOTH_WAYS - 1f) * MOST_TURN;
    }

    /**
     * Emits a cube centered on the pose's origin.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer, position and color
     * @param half     the cube's half-size
     * @param color    the packed ARGB color
     */
    public static void emitCube(PoseStack.Pose pose, VertexConsumer consumer, float half, int color) {
        for (float[] face : CUBE_FACES) {
            for (int corner = 0; corner < face.length; corner += AXES) {
                consumer.addVertex(pose, face[corner + X] * half, face[corner + Y] * half, face[corner + Z] * half)
                        .setColor(color);
            }
        }
    }

    /**
     * How much of the mote shows at a fade stage: all of it fresh, less at each stage after.
     *
     * @param fade the wisp's fade stage
     * @return the share, one fresh down to a quarter at the last stage
     */
    static float strength(int fade) {
        return 1f - (float) fade / (WispBlock.LAST_FADE + 1);
    }

    /**
     * How much of the mote shows as it fades in: nothing as it appears,
     * rising smoothly to all of it over the fade-in.
     *
     * @param age ticks since the wisp appeared on this client
     * @return the share, zero to one
     */
    static float fadeIn(float age) {
        float share = Mth.clamp(age / FADE_IN_TICKS, 0f, 1f);
        return share * share * (SMOOTHSTEP_RISE - SMOOTHSTEP_EASE * share);
    }

    /** Render state snapshot for a wisp: its fade stage, its age and its bob clock. */
    public static class WispRenderState extends BlockEntityRenderState {
        /** Ticks since the wisp appeared on this client. */
        public float age;
        /** The wisp's fade stage. */
        public int fade;
        /** The game clock in ticks. */
        public float time;
        /** The wisp's position seed, which sets its own bob phase, pace and turn. */
        public long seed;
    }
}
