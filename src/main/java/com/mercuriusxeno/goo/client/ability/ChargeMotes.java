package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Blast's charging up: while a charge-sized hold runs, motes in Unstable's
 * green fade in from nothing around the rooted spheroid and around the palm
 * blob at one steady rate, whatever the stage, and pull inward to be
 * absorbed at the shell and at the blob; the spheroid swells on the palm
 * blob's own beat, so the two pulse together. The motes and the pulse stop
 * as the charge releases or cancels.
 * decision blast-charge-pulls-green-motes-into-the-shell-and-the-blob
 */
public final class ChargeMotes {

    /** Unstable's green, the rim its explosion burns to (decision elemental-explosion-per-type). */
    static final int UNSTABLE_GREEN = 0x39FF14;
    /** Motes the shell draws in each tick. */
    static final int SHELL_MOTES_PER_TICK = 2;
    /** Motes the palm blob draws in each tick. */
    static final int BLOB_MOTES_PER_TICK = 1;
    /** The ticks a mote takes from fading in to being absorbed. */
    static final int LIFE_TICKS = 16;
    /** How far outside the shell a shell mote fades in, in blocks. */
    static final double SHELL_GATHER = 1.5;
    /** How far from the palm blob a blob mote fades in, in blocks. */
    static final double BLOB_GATHER = 0.6;
    /** The share of a mote's life it takes to fade in from nothing. */
    static final float FADE_IN_SHARE = 0.4f;
    /** The share of a mote's life it takes to fade out as it is absorbed. */
    static final float ABSORB_SHARE = 0.15f;
    /** How far the spheroid swells over the blob's beat, as a share of its radius. */
    static final double SWELL_DEPTH = 0.04;
    private static final float MOTE_HALF = 0.04f;
    private static final float MOTE_ALPHA = 0.85f;
    private static final int OPAQUE = 255;
    private static final double FULL_TURN = Math.PI * 2;
    private static final double HALF = 0.5;
    private static final double TOP_TO_BOTTOM = 2;

    private static final List<Mote> LIVE = new ArrayList<>();

    private ChargeMotes() {
    }

    /**
     * One mote on its way in.
     *
     * @param from      where it fades in
     * @param to        where it is absorbed, on the shell or at the blob
     * @param born      the game tick it spawned on
     * @param lifeTicks the ticks it takes to arrive
     * @param rgb       its color
     */
    public record Mote(Vec3 from, Vec3 to, long born, int lifeTicks, int rgb) {

        /**
         * Its alpha share at an age: nothing as it spawns, full once faded in,
         * and fading again as the shell or the blob absorbs it.
         *
         * @param age the ticks since it spawned, partial ticks included
         * @return the share, 0 to 1
         */
        public float alphaAt(double age) {
            float share = (float) Mth.clamp(age / lifeTicks, 0, 1);
            float in = Math.min(1f, share / FADE_IN_SHARE);
            float out = Math.min(1f, (1f - share) / ABSORB_SHARE);
            return Math.min(in, out);
        }

        /**
         * Where it stands at an age, pulling inward faster as it nears its end.
         *
         * @param age the ticks since it spawned, partial ticks included
         * @return its place
         */
        public Vec3 placeAt(double age) {
            double share = Mth.clamp(age / lifeTicks, 0, 1);
            return from.lerp(to, share * share);
        }

        /**
         * Whether it has been absorbed by a moment.
         *
         * @param now the game time, partial ticks included
         * @return true once its life has run
         */
        boolean absorbedBy(double now) {
            return now - born >= lifeTicks;
        }
    }

    /**
     * The motes one tick of the charge spawns: the shell's, fading in outside
     * it and ending on it, and the blob's, fading in around it and ending at it.
     *
     * @param gameTime    the game tick
     * @param rooted      the spheroid's center
     * @param shellRadius the spheroid's radius this tick
     * @param blob        the palm blob's place
     * @param random      the client's random
     * @return the motes spawned
     */
    static List<Mote> spawn(long gameTime, Vec3 rooted, double shellRadius, Vec3 blob, RandomSource random) {
        List<Mote> spawned = new ArrayList<>(SHELL_MOTES_PER_TICK + BLOB_MOTES_PER_TICK);
        for (int i = 0; i < SHELL_MOTES_PER_TICK; i++) {
            Vec3 heading = randomUnit(random);
            spawned.add(new Mote(rooted.add(heading.scale(shellRadius + SHELL_GATHER)),
                    rooted.add(heading.scale(shellRadius)), gameTime, LIFE_TICKS, UNSTABLE_GREEN));
        }
        for (int i = 0; i < BLOB_MOTES_PER_TICK; i++) {
            spawned.add(new Mote(blob.add(randomUnit(random).scale(BLOB_GATHER)), blob, gameTime, LIFE_TICKS,
                    UNSTABLE_GREEN));
        }
        return spawned;
    }

    /**
     * Where the spheroid stands in its beat: the palm blob's own phase.
     *
     * @param time the game time in ticks, partial ticks included
     * @return the phase, 0 to 1
     */
    static double pulsePhase(double time) {
        return LiftedPalmBlob.beatPhase(time);
    }

    /**
     * How far the spheroid swells at a moment, on the palm blob's beat.
     *
     * @param time the game time in ticks, partial ticks included
     * @return the share of its radius it draws at
     */
    public static double spheroidSwell(double time) {
        return 1 + SWELL_DEPTH * (HALF - HALF * Math.cos(FULL_TURN * pulsePhase(time)));
    }

    /**
     * Carries the live motes one tick on: the absorbed go, and this tick's spawn.
     *
     * @param gameTime    the game tick
     * @param anchor      the spot the charge rooted at
     * @param shellRadius the spheroid's radius this tick
     * @param blob        the palm blob's place
     * @param random      the client's random
     */
    public static void tick(long gameTime, TargetResult anchor, double shellRadius, Vec3 blob, RandomSource random) {
        LIVE.removeIf(mote -> mote.absorbedBy(gameTime));
        centerOf(anchor).ifPresent(center -> LIVE.addAll(spawn(gameTime, center, shellRadius, blob, random)));
    }

    /** Lets every live mote go, as the charge releases or cancels. */
    public static void clear() {
        LIVE.clear();
    }

    /**
     * Draws the live motes.
     *
     * @param poseStack the pose stack, camera relative
     * @param buffers   the buffer source
     * @param camera    the camera's world position
     * @param now       the game time, partial ticks included
     */
    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera, double now) {
        if (LIVE.isEmpty()) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.SPORE_SHELL_TYPE);
        for (Mote mote : LIVE) {
            double age = now - mote.born();
            Vec3 at = mote.placeAt(age).subtract(camera);
            int alpha = Math.round(Mth.clamp(mote.alphaAt(age) * MOTE_ALPHA, 0f, 1f) * OPAQUE);
            SporeMotes.emit(poseStack.last(), consumer, (float) at.x, (float) at.y, (float) at.z, MOTE_HALF,
                    ARGB.color(alpha, mote.rgb()));
        }
        buffers.endBatch(GooRenderTypes.SPORE_SHELL_TYPE);
    }

    /**
     * The spheroid's center for an anchor, as the held dome draws it.
     *
     * @param anchor the spot the charge rooted at
     * @return the center, empty where the anchor aims at nothing
     */
    static Optional<Vec3> centerOf(TargetResult anchor) {
        return Optional.ofNullable(HeldDomeRenderer.anchorOf(anchor))
                .map(dome -> dome.domeCorner().add(HALF, HALF, HALF));
    }

    private static Vec3 randomUnit(RandomSource random) {
        double y = random.nextDouble() * TOP_TO_BOTTOM - 1;
        double ring = Math.sqrt(Math.max(0, 1 - y * y));
        double around = random.nextDouble() * FULL_TURN;
        return new Vec3(Math.cos(around) * ring, y, Math.sin(around) * ring);
    }
}
