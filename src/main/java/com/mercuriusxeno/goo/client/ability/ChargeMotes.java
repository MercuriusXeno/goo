package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.ber.WispRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
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
    /** The ticks between the palm blob's motes: one every other tick, so they read as wisps, not noise. */
    static final int BLOB_MOTE_EVERY_TICKS = 2;
    /** The ticks a mote takes from fading in to being absorbed. */
    static final int LIFE_TICKS = 16;
    /** How far outside the shell a shell mote fades in, in blocks. */
    static final double SHELL_GATHER = 1.5;
    /** How far from the palm blob a blob wisp fades in, in blocks: close around the held blob. */
    static final double BLOB_GATHER = 0.3;
    /** The share of a shell mote's life it takes to fade in from nothing. */
    static final float FADE_IN_SHARE = 0.4f;
    /** The share of a blob mote's life it takes to fade in, longer than a shell mote's so it eases in. */
    static final float BLOB_FADE_IN_SHARE = 0.6f;
    /** The ticks a mote still on its way in at the release takes to be pushed out and fade to nothing. */
    static final int REPEL_TICKS = 6;
    /** How far a released mote is pushed back out over its repel, against how far it had left to travel. */
    static final double REPEL_SHARE = 0.5;
    /** The repel moment of a mote the release has not touched. */
    static final long NOT_REPELLED = Long.MIN_VALUE;
    /** The share of a mote's life it takes to fade out as it is absorbed. */
    static final float ABSORB_SHARE = 0.15f;
    /** How far the spheroid swells over the blob's beat, as a share of its radius. */
    static final double SWELL_DEPTH = 0.04;
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
     * @param haloHalf   its wisp's halo half-size, in blocks
     * @param atTheBlob  whether the palm blob absorbs it, rather than the shell
     * @param repelledAt the game tick the release pushed it back out, NOT_REPELLED while it heads in
     */
    public record Mote(Vec3 from, Vec3 to, long born, int lifeTicks, int rgb, float haloHalf, boolean atTheBlob,
                       long repelledAt) {

        /**
         * This mote pushed back out from the release on, fading as it goes.
         * The operator's ruling of 2026-11-15: wisps mid-cycle at an early release repel and fade, briefly.
         *
         * @param released the game tick the charge released
         * @return the repelled mote
         */
        public Mote repelledAt(long released) {
            return new Mote(from, to, born, lifeTicks, rgb, haloHalf, atTheBlob, released);
        }

        private boolean repelled() {
            return repelledAt != NOT_REPELLED;
        }

        /**
         * Its alpha share at an age: nothing as it spawns, full once faded in,
         * and fading again as the shell or the blob absorbs it; once repelled,
         * the share it held at the release, falling to nothing over the repel.
         *
         * @param age the ticks since it spawned, partial ticks included
         * @return the share, 0 to 1
         */
        public float alphaAt(double age) {
            if (repelled()) {
                double sinceRelease = born + age - repelledAt;
                float left = (float) Mth.clamp(1 - sinceRelease / REPEL_TICKS, 0, 1);
                return headingInAlphaAt(repelledAt - born) * left;
            }
            return headingInAlphaAt(age);
        }

        private float headingInAlphaAt(double age) {
            float share = (float) Mth.clamp(age / lifeTicks, 0, 1);
            float in = Math.min(1f, share / (atTheBlob ? BLOB_FADE_IN_SHARE : FADE_IN_SHARE));
            float out = Math.min(1f, (1f - share) / ABSORB_SHARE);
            return Math.min(in, out);
        }

        /**
         * Where it stands at an age, pulling inward faster as it nears its
         * end; once repelled, pushed back out from where the release caught
         * it, slowing as the push spends itself.
         *
         * @param age the ticks since it spawned, partial ticks included
         * @return its place
         */
        public Vec3 placeAt(double age) {
            if (!repelled()) {
                return headingInPlaceAt(age);
            }
            Vec3 caught = headingInPlaceAt(repelledAt - born);
            Vec3 outward = caught.subtract(to);
            Vec3 heading = (outward.lengthSqr() > 0 ? outward : from.subtract(to)).normalize();
            double pushed = Mth.clamp((born + age - repelledAt) / REPEL_TICKS, 0, 1);
            double eased = 1 - (1 - pushed) * (1 - pushed);
            return caught.add(heading.scale(from.distanceTo(to) * REPEL_SHARE * eased));
        }

        private Vec3 headingInPlaceAt(double age) {
            double share = Mth.clamp(age / lifeTicks, 0, 1);
            return from.lerp(to, share * share);
        }

        /**
         * Whether it has been absorbed by a moment, or faded out from its repel.
         *
         * @param now the game time, partial ticks included
         * @return true once its life or its repel has run
         */
        boolean absorbedBy(double now) {
            return repelled() ? now - repelledAt >= REPEL_TICKS : now - born >= lifeTicks;
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
        List<Mote> spawned = new ArrayList<>(SHELL_MOTES_PER_TICK + 1);
        for (int i = 0; i < SHELL_MOTES_PER_TICK; i++) {
            Vec3 heading = randomUnit(random);
            spawned.add(new Mote(rooted.add(heading.scale(shellRadius + SHELL_GATHER)),
                    rooted.add(heading.scale(shellRadius)), gameTime, LIFE_TICKS, UNSTABLE_GREEN, SHELL_HALO_HALF, false,
                    NOT_REPELLED));
        }
        if (gameTime % BLOB_MOTE_EVERY_TICKS == 0) {
            spawned.add(new Mote(blob.add(randomUnit(random).scale(BLOB_GATHER)), blob, gameTime, LIFE_TICKS,
                    UNSTABLE_GREEN, BLOB_HALO_HALF, true, NOT_REPELLED));
        }
        return spawned;
    }

    /** A shell wisp's halo half-size, in blocks: the wisps around the blast zone draw larger. */
    static final float SHELL_HALO_HALF = 0.13f;
    /** A blob wisp's halo half-size, in blocks: small around the held blob. */
    static final float BLOB_HALO_HALF = 0.02f;
    /** A wisp's core half-size against its halo's, as a radiant wisp's core stands to its halo. */
    static final float CORE_SHARE = 0.4f;
    /** The paler green a wisp's core burns, as a radiant wisp's core is paler than its halo. */
    static final int PALE_GREEN = 0xC8FFB4;
    private static final int HALO_ALPHA = 70;
    private static final int CORE_ALPHA = 210;

    /**
     * Emits a wisp at a share of its alpha: a pale green core cube inside a
     * larger halo cube in Unstable's green, the look a radiant wisp has.
     * decision blast-charge-pulls-green-motes-into-the-shell-and-the-blob
     *
     * @param pose     the pose at the wisp's center
     * @param consumer the vertex consumer, position and color
     * @param haloHalf the wisp's halo half-size, in blocks
     * @param share    the wisp's alpha share, 0 to 1
     */
    public static void emitWisp(PoseStack.Pose pose, VertexConsumer consumer, float haloHalf, float share) {
        WispRenderer.emitCube(pose, consumer, haloHalf, ARGB.color(Math.round(HALO_ALPHA * share), UNSTABLE_GREEN));
        WispRenderer.emitCube(pose, consumer, haloHalf * CORE_SHARE,
                ARGB.color(Math.round(CORE_ALPHA * share), PALE_GREEN));
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
        return swellAtPhase(pulsePhase(time));
    }

    /**
     * How far the spheroid swells at a point of the beat: not at all at its
     * start, most at its middle, as the palm blob does.
     * decision every-lifted-palm-blob-beats-once-a-second
     *
     * @param phase the beat's phase, 0 to 1
     * @return the share of its radius it draws at
     */
    static double swellAtPhase(double phase) {
        return 1 + SWELL_DEPTH * (HALF - HALF * Math.cos(FULL_TURN * phase));
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

    /**
     * The live motes the level's pass draws.
     *
     * @param blobDrawsInTheLevel whether the palm blob draws in the level's pass
     * @return the motes
     */
    static List<Mote> levelWisps(boolean blobDrawsInTheLevel) {
        return LIVE.stream().filter(mote -> blobDrawsInTheLevel || !mote.atTheBlob()).toList();
    }

    /**
     * A blob wisp as the hand's pass draws it beside the local first-person
     * palm blob, so the two depth test against each other.
     *
     * @param offset   where the wisp stands from the blob, in blocks along the world's axes
     * @param haloHalf its halo half-size, in blocks
     * @param share    its alpha share, 0 to 1
     */
    public record HandWisp(Vec3 offset, float haloHalf, float share) {
    }

    /**
     * The blob's live wisps for the hand's pass to draw about the blob, which
     * draws there in first person after the level, so wisps the level drew
     * would land behind it whatever their depth.
     * decision blast-charge-pulls-green-motes-into-the-shell-and-the-blob
     *
     * @param now the game time, partial ticks included
     * @return the wisps, each placed from the blob
     */
    public static List<HandWisp> blobWispsInTheHandPass(double now) {
        return LIVE.stream().filter(Mote::atTheBlob).map(mote -> {
            double age = now - mote.born();
            return new HandWisp(mote.placeAt(age).subtract(mote.to()), mote.haloHalf(),
                    Mth.clamp(mote.alphaAt(age), 0f, 1f));
        }).toList();
    }

    /** Lets every live mote go at once. */
    public static void clear() {
        LIVE.clear();
    }

    /**
     * The charge released or cancelled: no mote spawns, each still on its way in is pushed back out to fade over
     * the repel, and each repel run out goes.
     * The operator's ruling of 2026-11-15: wisps mid-cycle at an early release repel and fade, briefly.
     *
     * @param gameTime the game tick
     */
    public static void release(long gameTime) {
        LIVE.replaceAll(mote -> mote.repelledAt == NOT_REPELLED ? mote.repelledAt(gameTime) : mote);
        LIVE.removeIf(mote -> mote.absorbedBy(gameTime));
    }

    /**
     * Queues the live motes to draw as wisps after the translucent blocks.
     *
     * @param now the game time, partial ticks included
     */
    public static void render(double now) {
        Minecraft mc = Minecraft.getInstance();
        boolean blobDrawsInTheLevel = mc.player == null || LiftedPalmBlob.drawsInTheLevel(mc.player.getId());
        for (Mote mote : levelWisps(blobDrawsInTheLevel)) {
            double age = now - mote.born();
            float share = Mth.clamp(mote.alphaAt(age), 0f, 1f);
            // blast-charge-pulls-green-motes-into-the-shell-and-the-blob: wisps draw after the translucent blocks
            WispGlow.queue(new WispGlow.Sprite(mote.placeAt(age), 0f,
                    (pose, consumer) -> emitWisp(pose, consumer, mote.haloHalf(), share)));
        }
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
