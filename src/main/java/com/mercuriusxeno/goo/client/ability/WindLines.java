package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.WindStep;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.particle.VoidMoteParticle;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Line-drawn wind for a held stream that names it: each held tick a few
 * thick lines, white and very light gray tinted slightly blue, rush straight
 * out of the glove along the cone, swaying slightly off course, then carry
 * on, slowing, as they twist slightly about their course and fade, the tail
 * drawing in behind the head; drawn as smooth curves with no corner in them
 * (the operator's ruling: twist slightly, never bend square into a loop),
 * and where the stream asks, snowflakes flit
 * weightlessly along them. Typhoon's streams reuse the lines without the
 * snowflakes. Vacuum plays the same lines back into the blob in void colors,
 * with void motes flying loose from its cone.
 * cold-streams-wind-lines-and-snowflakes
 * vacuum-wind-lines-are-gusts-streaks-flying-into-the-blob
 */
public final class WindLines {

    /** The client's wind. */
    public static final WindLines CLIENT = new WindLines();

    /** Wind lines a held tick blows on average: a few lines at a time, not a blizzard. */
    static final double LINES_PER_TICK = 0.45;
    /**
     * How much faster than its average pace a line leaves the glove: it
     * launches this share faster and slows over its straight run, covering
     * the same ground before it curls.
     */
    static final double LAUNCH_SURGE = 0.35;
    /** The stream's wind: its volume and pitch, and the held ticks it outlasts a let-go by. */
    static final float WIND_VOLUME = 0.7f;
    static final float WIND_PITCH = 1.0f;
    private static final long WIND_LINGER_TICKS = 2;
    /** Ticks the wind takes to fade to silence once the stream is let go. */
    static final int WIND_FADE_TICKS = 15;
    /** Ticks a line lives, from leaving the glove to fading out. */
    static final int LIFE_TICKS = 50;
    /** The share of a line's life it rushes straight before it curls. */
    static final double CURL_STARTS = 0.5;
    /** The ticks a line rushes straight before it curls. */
    static final double STRAIGHT_TICKS = LIFE_TICKS * CURL_STARTS;
    /** The share of the cone's length a line rushes straight before it curls. */
    static final double STRAIGHT_SHARE = 0.6;
    /** The ticks a line twists for after its straight run, until it fades. */
    static final double TWIST_TICKS = LIFE_TICKS - STRAIGHT_TICKS;
    /** How far off its axis a line has twisted when it fades, in blocks: slight. */
    static final double TWIST_RADIUS = 0.15;
    /** The share of a turn a line twists through before it fades. */
    static final double TWIST_TURNS = 0.35;
    /** How much of its pace a twisting line sheds by the time it fades: half. */
    static final double TWIST_EASE = 0.5;
    /** Ticks of the path the trailing line spans behind its head while it rushes straight. */
    static final double TAIL_TICKS = 10;
    /** Points the line draws through, enough that its curve reads smooth. */
    private static final int TAIL_SAMPLES = 32;
    static final float LINE_WIDTH = 3.5f;
    /** Snowflakes a line drops along itself each tick it lives. */
    private static final float SNOWFLAKES_PER_LINE_TICK = 0.2f;
    private static final int MAX_ALPHA = 200;
    /**
     * The turn an inward line makes about its gust's axis over its run: an
     * eighth of a turn, one way, for the vortex without a loop.
     * vacuum-wind-lines-run-straight-and-far-in-void-colors-with-motes
     * operator ruling: twist just slightly less, a flatter curve
     */
    static final double INWARD_TWIST = 0.12 * 2 * Math.PI;
    /**
     * How many times Gust's launch pace an inward line flies into the blob at.
     * operator ruling: speed up their approach a bit more
     */
    static final double INWARD_PACE = 1.4;
    /** Void motes a held tick of Vacuum flies loose from the cone. */
    private static final int MOTES_PER_TICK = 3;
    /** The nearest a loose mote starts to the blob, as a share of the cone's range squared. */
    private static final double MOTE_NEAREST_SHARE = 0.01;
    /** An updraft column's wind: lines rising out of the floor in the wind's colors, no snowflakes. */
    private static final WindStep RISING_WIND = new WindStep(false);
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;
    private static final double NEAR_VERTICAL = 0.99;
    /** How far toward a column's edge an updraft's line may leave, as a share of its half width. */
    static final double RISE_INSET = 0.8;
    /** The narrow cone an updraft's lines rise through, in degrees, so they lean a little as they climb. */
    static final double RISE_CONE = 10;
    /** A unit random spread across both sides, -1 to 1. */
    private static final double BOTH_SIDES = 2;

    /** The two ways a twist winds. */
    private static final double CLOCKWISE = 1;
    private static final double COUNTERCLOCKWISE = -1;
    /** The most a line sways off its course, in blocks, each of its two drifts. */
    static final double SWAY = 0.18;
    /** The slowest and the fastest a sway drifts, in radians a tick: a pull and an ebb over the line's life. */
    private static final double SWAY_RATE_MIN = 0.12;
    private static final double SWAY_RATE_SPAN = 0.18;

    /**
     * A line's sway: two slow drifts in sideways directions of its own.
     *
     * @param first       the first drift's direction, scaled to its reach
     * @param second      the second drift's direction, scaled to its reach
     * @param firstPhase  where the first drift starts, in radians
     * @param secondPhase where the second drift starts, in radians
     * @param firstRate   how fast the first drift turns, in radians a tick
     * @param secondRate  how fast the second drift turns, in radians a tick
     */
    record Sway(Vec3 first, Vec3 second, double firstPhase, double secondPhase, double firstRate,
                double secondRate) {

        /** No sway at all. */
        static final Sway NONE = new Sway(Vec3.ZERO, Vec3.ZERO, 0, 0, 0, 0);
    }

    /**
     * One line of wind.
     *
     * @param origin     where it leaves, in the world
     * @param axis       the unit direction it rushes along
     * @param outward    the unit direction out from the cone's middle, square to the look, it curls toward
     * @param across     the unit direction square to the look and to outward, which with outward faces the player
     * @param straight   blocks it rushes straight before curling
     * @param sway       how it sways off its course
     * @param winding    which way its curl winds, 1 or -1
     * @param gray       how far its color leans from white to pale gray, 0 to 1
     * @param snowflakes whether snowflakes flit along it
     * @param startTick  the game time it left the glove
     * @param inward     whether it plays its course back from the far end into the blob rather than out of it
     */
    record Line(Vec3 origin, Vec3 axis, Vec3 outward, Vec3 across, double straight, Sway sway, double winding,
                float gray, boolean snowflakes, long startTick, boolean inward,
                WindStep.LineColors colors, boolean followsTheBlob) {

        Line(Vec3 origin, Vec3 axis, Vec3 outward, Vec3 across, double straight, Sway sway, double winding,
             float gray, boolean snowflakes, long startTick, boolean inward,
             WindStep.LineColors colors) {
            this(origin, axis, outward, across, straight, sway, winding, gray, snowflakes, startTick, inward,
                    colors, false);
        }

        /**
         * This line marked as blown from the lifted palm blob, so its start
         * follows the blob where it stands through its first tick.
         *
         * @return the marked line
         */
        Line followingTheBlob() {
            return new Line(origin, axis, outward, across, straight, sway, winding, gray, snowflakes, startTick,
                    inward, colors, true);
        }

        /**
         * A line in the wind's white and pale gray.
         *
         * @param origin     where it leaves, in the world
         * @param axis       the unit direction it rushes along
         * @param outward    the unit direction out from the cone's middle it curls toward
         * @param across     the unit direction square to the look and to outward
         * @param straight   blocks it rushes straight before curling
         * @param sway       how it sways off its course
         * @param winding    which way its curl winds, 1 or -1
         * @param gray       how far its color leans from white to pale gray, 0 to 1
         * @param snowflakes whether snowflakes flit along it
         * @param startTick  the game time it left the glove
         * @param inward     whether it plays its course back from the far end into the blob rather than out of it
         */
        Line(Vec3 origin, Vec3 axis, Vec3 outward, Vec3 across, double straight, Sway sway, double winding,
             float gray, boolean snowflakes, long startTick, boolean inward) {
            this(origin, axis, outward, across, straight, sway, winding, gray, snowflakes, startTick, inward,
                    WindStep.LineColors.WIND);
        }

        /**
         * A line rushing out of the glove.
         *
         * @param origin     where it leaves, in the world
         * @param axis       the unit direction it rushes along
         * @param outward    the unit direction out from the cone's middle it curls toward
         * @param across     the unit direction square to the look and to outward
         * @param straight   blocks it rushes straight before curling
         * @param sway       how it sways off its course
         * @param winding    which way its curl winds, 1 or -1
         * @param gray       how far its color leans from white to pale gray, 0 to 1
         * @param snowflakes whether snowflakes flit along it
         * @param startTick  the game time it left the glove
         */
        Line(Vec3 origin, Vec3 axis, Vec3 outward, Vec3 across, double straight, Sway sway, double winding,
             float gray, boolean snowflakes, long startTick) {
            this(origin, axis, outward, across, straight, sway, winding, gray, snowflakes, startTick, false,
                    WindStep.LineColors.WIND);
        }
    }

    private final List<Line> live = new ArrayList<>();
    /** The stream's continuous wind while it plays. */
    private final AtomicReference<StreamWind> wind = new AtomicReference<>();
    /** The game time the stream was last held, which its wind outlasts by WIND_LINGER_TICKS. */
    private long windHeldAt;
    /** The game time snowflakes last dropped, so a tick drawn over several frames drops them once. */
    private long snowflakesDroppedAt = -1;
    /** The pitch the playing wind sounds at, as its wind step names it: low for Vacuum's suction. */
    private volatile float windPitch = WIND_PITCH;

    private WindLines() {
    }

    /**
     * Where a line's head stands a number of ticks after it left the glove:
     * along its axis for its straight run, then still carrying on along it,
     * slowing, while it eases into a slight twist about its course, the
     * twist's reach growing from nothing so the line bends a little at a time
     * and never turns a corner; all the while swaying slightly off course.
     *
     * @param line the line
     * @param age  ticks since it left the glove
     * @return the head's world position
     */
    static Vec3 pathPoint(Line line, double age) {
        double clamped = line.inward() ? Math.clamp(age, -TAIL_TICKS, arrivalTicks(line))
                : Math.clamp(age, 0, LIFE_TICKS);
        return coursePoint(line, clamped).add(swayAt(line, clamped));
    }

    private static Vec3 coursePoint(Line line, double age) {
        if (line.inward()) {
            return inwardCoursePoint(line, age);
        }
        if (age <= STRAIGHT_TICKS) {
            return line.origin().add(line.axis().scale(reachAt(line, age)));
        }
        double twisted = (age - STRAIGHT_TICKS) / TWIST_TICKS;
        double radius = TWIST_RADIUS * twisted * twisted;
        double angle = line.winding() * TWIST_TURNS * TWO_PI * twisted;
        return line.origin().add(line.axis().scale(reachAt(line, age)))
                .add(line.outward().scale(radius * Math.cos(angle)))
                .add(line.across().scale(radius * Math.sin(angle)));
    }

    /**
     * Where an inward line's head stands: leaving the far end of Gust's run
     * at its inward pace and holding it into the blob, with no slow start;
     * before its start, where its tail trails, it stands out beyond the far
     * end on the same course; turned gently one way about the gust's axis as
     * it draws in.
     * vacuum-wind-lines-are-gusts-streaks-flying-into-the-blob
     * vacuum-wind-lines-run-straight-and-far-in-void-colors-with-motes
     * operator ruling: launch fast, hold pace into the blob
     *
     * @param line the inward line
     * @param age  ticks since it started, from -TAIL_TICKS to its arrival
     * @return the head's world position
     */
    private static Vec3 inwardCoursePoint(Line line, double age) {
        double run = reachAt(line, LIFE_TICKS);
        double reach = Math.max(0, run - inwardPace(line) * age);
        double drawn = 1 - reach / run;
        Vec3 out = line.axis().scale(reach);
        return line.origin().add(turnedAbout(out, gustAxisOf(line), line.winding() * INWARD_TWIST * drawn));
    }

    /**
     * The pace a line's head leaves at: Gust's launch, LAUNCH_SURGE faster
     * than its straight run's average.
     *
     * @param line the line
     * @return the pace, in blocks a tick
     */
    static double launchPace(Line line) {
        return line.straight() * (1 + LAUNCH_SURGE) / STRAIGHT_TICKS;
    }

    /**
     * The pace an inward line's head flies into the blob at, held from its
     * first tick: INWARD_PACE times Gust's launch.
     * operator ruling: launch fast, hold pace into the blob
     *
     * @param line the inward line
     * @return the pace, in blocks a tick
     */
    static double inwardPace(Line line) {
        return INWARD_PACE * launchPace(line);
    }

    /**
     * The ticks an inward line's head takes to cross Gust's run into the blob.
     *
     * @param line the inward line
     * @return the ticks until its head lands on the blob
     */
    static double arrivalTicks(Line line) {
        return reachAt(line, LIFE_TICKS) / inwardPace(line);
    }

    /**
     * The ticks a line lives: an outward line LIFE_TICKS, an inward line until
     * its head lands on the blob and its tail has drawn in after it.
     *
     * @param line the line
     * @return its life, in ticks
     */
    static double lifeOf(Line line) {
        return line.inward() ? arrivalTicks(line) + TAIL_TICKS : LIFE_TICKS;
    }

    /**
     * How far along its axis an outward line's head stands from the glove:
     * its launched straight run, then carrying on, slowing, as it twists.
     *
     * @param line the line
     * @param age  ticks since it left the glove, 0 to LIFE_TICKS
     * @return the distance along its axis, in blocks
     */
    static double reachAt(Line line, double age) {
        if (age <= STRAIGHT_TICKS) {
            return line.straight() * launched(age / STRAIGHT_TICKS);
        }
        return line.straight() + twistAdvance(line, (age - STRAIGHT_TICKS) / TWIST_TICKS);
    }

    /**
     * How far past its straight run a twisting line has carried on along its
     * axis: leaving at the pace its straight run ended at and easing to half
     * that by the time it fades, so it never stops to turn.
     *
     * @param line     the line
     * @param twisted  the share of its twist gone, 0 to 1
     * @return the distance past the straight run, in blocks
     */
    static double twistAdvance(Line line, double twisted) {
        double endPace = line.straight() * (1 - LAUNCH_SURGE) / STRAIGHT_TICKS;
        return endPace * TWIST_TICKS * (twisted - twisted * twisted * TWIST_EASE * HALF);
    }

    /**
     * How far a line has swayed off its course: its two slow drifts, growing
     * from nothing at the glove to whole as it leaves its straight run.
     *
     * @param line the line
     * @param age  ticks since it left the glove
     * @return the sway's offset
     */
    static Vec3 swayAt(Line line, double age) {
        Sway sway = line.sway();
        if (line.inward()) {
            // vacuum-wind-lines-run-straight-and-far-in-void-colors-with-motes: no sway, so no loops
            return Vec3.ZERO;
        }
        double growth = Math.min(1, age / STRAIGHT_TICKS);
        return sway.first().scale(Math.sin(sway.firstRate() * age + sway.firstPhase()) * growth)
                .add(sway.second().scale(Math.sin(sway.secondRate() * age + sway.secondPhase()) * growth));
    }

    /**
     * Where a loose void mote starts: a random point inside the stream's
     * cone, out to its range and within its half angle of the look, spread
     * across the cone rather than bunched at its tip.
     * vacuum-motes-fly-loose-from-the-cone-to-the-blob
     *
     * @param gust   the stream's cone
     * @param random the random source
     * @return the mote's start
     */
    static Vec3 moteStart(Gust gust, RandomSource random) {
        double off = Math.toRadians(gust.coneDegrees() * HALF) * Math.sqrt(random.nextDouble());
        Vec3 direction = tilt(gust.axis(), off, random.nextDouble() * TWO_PI);
        double out = gust.range() * Math.sqrt(MOTE_NEAREST_SHARE + (1 - MOTE_NEAREST_SHARE) * random.nextDouble());
        return gust.origin().add(direction.scale(out));
    }

    /**
     * How far a line rushes straight before it twists: a random share of its
     * gust's range, inward or out, so Vacuum's streak runs the length Gust's does.
     * vacuum-wind-lines-are-gusts-streaks-flying-into-the-blob
     *
     * @param range  the gust's range, in blocks
     * @param spread a random draw from 0 to 1 sizing the run
     * @return the straight run, in blocks
     */
    static double runOf(double range, double spread) {
        return range * STRAIGHT_SHARE * (HALF + spread);
    }

    /**
     * The axis of the gust a line was blown along: square to the line's
     * outward and across, which the gust's look built.
     *
     * @param line the line
     * @return the gust's unit axis
     */
    static Vec3 gustAxisOf(Line line) {
        return line.outward().cross(line.across()).normalize();
    }

    /**
     * A vector turned about a unit axis by an angle, right-handed.
     *
     * @param vector the vector
     * @param axis   the unit axis
     * @param angle  the angle, in radians
     * @return the turned vector
     */
    static Vec3 turnedAbout(Vec3 vector, Vec3 axis, double angle) {
        double cos = Math.cos(angle);
        return vector.scale(cos).add(axis.cross(vector).scale(Math.sin(angle)))
                .add(axis.scale(axis.dot(vector) * (1 - cos)));
    }

    /**
     * How far behind its head a line's tail trails: the full tail while the
     * line rushes straight, drawing in to nothing over its curl, so the tail
     * meets the head at the curl's center as the line ends.
     *
     * @param age ticks since the line left the glove
     * @return the tail's lag behind the head, in ticks of the path
     */
    static double tailLag(double age) {
        if (age <= STRAIGHT_TICKS) {
            return TAIL_TICKS;
        }
        double curled = Math.min(1, (age - STRAIGHT_TICKS) / (LIFE_TICKS - STRAIGHT_TICKS));
        return TAIL_TICKS * (1 - curled);
    }

    /**
     * How far behind its head a line's tail trails: an inward line its whole
     * tail from its first tick, drawing in after its head onto the blob.
     * operator ruling: launch fast, hold pace into the blob
     *
     * @param line the line
     * @param age  ticks since the line started
     * @return the tail's lag behind the head, in ticks of the path
     */
    static double tailLagOf(Line line, double age) {
        return line.inward() ? TAIL_TICKS : tailLag(age);
    }

    /**
     * How far a line has faded at a share of its life: an outward line fades
     * over its life; an inward line draws at Gust's launch brightness all its
     * life, its tail drawing in onto the blob as it ends.
     * operator ruling: launch fast, hold pace into the blob
     *
     * @param line the line
     * @param life the share of its life it has lived, 0 to 1
     * @return the share of its brightness lost, 0 to 1
     */
    static float fadeOf(Line line, float life) {
        return line.inward() ? 0f : life;
    }

    /**
     * Blows this held tick's wind for the local player's stream when its ability names wind.
     *
     * @param player    the streaming player
     * @param abilityId the streamed ability
     * @param area      the ability's area, whose size and angle the wind fills
     * @param apex      the glove hand the stream leaves from
     */
    public static void blow(Player player, String abilityId, AbilityArea area, Vec3 apex) {
        windOf(abilityId).ifPresent(wind -> {
            Minecraft mc = Minecraft.getInstance();
            Gust gust = new Gust(apex, player.getLookAngle(), area.size(), area.angle());
            CLIENT.add(player.level().getRandom(), gust, wind, player.level().getGameTime(), true);
            if (wind.voidMotes()) {
                flyLooseMotes(mc, player.level().getRandom(), gust);
            }
            CLIENT.windPitch = wind.pitch();
            CLIENT.keepWindBlowing(player);
        });
    }

    /**
     * Blows one line up an updraft's column on a blowing tick: it leaves the
     * column's floor somewhere inside its width and rushes straight up before
     * curling out, no snowflakes on it.
     * updraft-blob-stands-a-column-of-wind
     *
     * @param random the random source
     * @param base   the middle of the column's floor
     * @param radius the column's half width
     * @param height the column's height
     * @param now    the game time
     */
    public void rise(RandomSource random, Vec3 base, double radius, double height, long now) {
        add(random, riseGust(base, radius, height, bothSides(random.nextDouble()), bothSides(random.nextDouble())),
                RISING_WIND, now);
    }

    private static double bothSides(double unit) {
        return unit * BOTH_SIDES - 1;
    }

    /**
     * An updraft's gust: from a point of the column's floor, straight up
     * through a narrow cone the column's height tall.
     * updraft-blob-stands-a-column-of-wind
     *
     * @param base   the middle of the column's floor
     * @param radius the column's half width
     * @param height the column's height
     * @param alongX where across the floor it leaves on x, -1 to 1 of the width inside the edge
     * @param alongZ where across the floor it leaves on z, -1 to 1 of the width inside the edge
     * @return the gust
     */
    static Gust riseGust(Vec3 base, double radius, double height, double alongX, double alongZ) {
        double reach = radius * RISE_INSET;
        Vec3 origin = base.add(alongX * reach, 0, alongZ * reach);
        return new Gust(origin, new Vec3(0, 1, 0), height, RISE_CONE);
    }

    /**
     * Where a held tick's wind blows from and along.
     *
     * @param origin      where the lines leave, in the world
     * @param axis        the unit direction they rush along
     * @param range       how far the cone they fill reaches, in blocks
     * @param coneDegrees the cone's apex angle, in degrees
     */
    record Gust(Vec3 origin, Vec3 axis, double range, double coneDegrees) {
    }

    /**
     * Keeps the stream's continuous wind playing while the stream is held,
     * starting it the first held tick.
     *
     * @param player the streaming player
     */
    private void keepWindBlowing(Player player) {
        windHeldAt = player.level().getGameTime();
        StreamWind playing = wind.get();
        if (playing == null || playing.isStopped()) {
            playing = new StreamWind(player);
            wind.set(playing);
            Minecraft.getInstance().getSoundManager().play(playing);
        }
    }

    /**
     * The stream's wind volume a number of ticks after it was let go: whole
     * while held, then falling evenly to nothing over WIND_FADE_TICKS.
     *
     * @param sinceLetGo ticks since the stream was let go, zero or less while it is held
     * @return the volume
     */
    static float windVolume(long sinceLetGo) {
        if (sinceLetGo <= 0) {
            return WIND_VOLUME;
        }
        return WIND_VOLUME * Math.max(0f, 1f - (float) sinceLetGo / WIND_FADE_TICKS);
    }

    /** The stream's continuous wind, following the player and fading once the stream is let go. */
    private final class StreamWind extends AbstractTickableSoundInstance {

        private final Player player;

        StreamWind(Player player) {
            super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = WIND_VOLUME;
            this.pitch = windPitch;
            follow();
        }

        @Override
        public void tick() {
            if (player.isRemoved()) {
                stop();
                return;
            }
            long sinceLetGo = player.level().getGameTime() - windHeldAt - WIND_LINGER_TICKS;
            this.volume = windVolume(sinceLetGo);
            // vacuum-sounds-a-suction-wind-and-rising-slurps: a wind step names the pitch its held wind sounds at
            this.pitch = windPitch;
            if (this.volume <= 0f) {
                stop();
            } else {
                follow();
            }
        }

        private void follow() {
            this.x = player.getX();
            this.y = player.getY();
            this.z = player.getZ();
        }
    }

    private void add(RandomSource random, Gust gust, WindStep wind, long now) {
        add(random, gust, wind, now, false);
    }

    private void add(RandomSource random, Gust gust, WindStep wind, long now, boolean fromTheBlob) {
        if (!blowsOn(now)) {
            return;
        }
        Vec3 apex = gust.origin();
        Vec3 look = gust.axis();
        double range = gust.range();
        double spread = Math.toRadians(gust.coneDegrees() * HALF);
        double about = random.nextDouble() * TWO_PI;
        Vec3 axis = tilt(look, spread * Math.sqrt(random.nextDouble()), about);
        Vec3 outward = radial(look, about);
        Vec3 across = look.cross(outward).normalize();
        double straight = runOf(range, random.nextDouble());
        double winding = random.nextBoolean() ? CLOCKWISE : COUNTERCLOCKWISE;
        Line line = new Line(apex, axis, outward, across, straight, sway(random, axis), winding, random.nextFloat(),
                wind.snowflakes(), now, wind.inward(), wind.colors());
        live.add(fromTheBlob ? line.followingTheBlob() : line);
    }

    /**
     * A line's own sway: two drifts in random directions square to its axis,
     * each starting and turning at its own random pace.
     *
     * @param random the random source
     * @param axis   the line's axis
     * @return the sway
     */
    private static Sway sway(RandomSource random, Vec3 axis) {
        return new Sway(radial(axis, random.nextDouble() * TWO_PI).scale(SWAY),
                radial(axis, random.nextDouble() * TWO_PI).scale(SWAY), random.nextDouble() * TWO_PI,
                random.nextDouble() * TWO_PI, SWAY_RATE_MIN + random.nextDouble() * SWAY_RATE_SPAN,
                SWAY_RATE_MIN + random.nextDouble() * SWAY_RATE_SPAN);
    }

    /**
     * A unit direction square to another, at an angle about it.
     *
     * @param axis  the unit direction
     * @param about the angle about it, in radians
     * @return the square direction
     */
    static Vec3 radial(Vec3 axis, double about) {
        Vec3 side = sideOf(axis);
        Vec3 up = axis.cross(side).normalize();
        return side.scale(Math.cos(about)).add(up.scale(Math.sin(about)));
    }

    /**
     * Whether a held tick blows a wind line: LINES_PER_TICK on average,
     * spread evenly over the ticks.
     *
     * @param gameTime the game time
     * @return true on a blowing tick
     */
    static boolean blowsOn(long gameTime) {
        return Math.floor(gameTime * LINES_PER_TICK) > Math.floor((gameTime - 1) * LINES_PER_TICK);
    }

    /**
     * The share of its straight run a line has covered at a share of its
     * straight time: it launches LAUNCH_SURGE faster than its average pace
     * and slows, covering the whole run as the time runs out.
     *
     * @param time the share of the straight run's ticks gone, 0 to 1
     * @return the share of its length covered, 0 to 1
     */
    static double launched(double time) {
        return time + LAUNCH_SURGE * time * (1 - time);
    }

    /**
     * Tilts a direction off itself by an angle, about its own axis by another.
     *
     * @param look  the unit direction
     * @param off   the angle off it, in radians
     * @param about the angle about it, in radians
     * @return the tilted unit direction
     */
    static Vec3 tilt(Vec3 look, double off, double about) {
        return look.scale(Math.cos(off)).add(radial(look, about).scale(Math.sin(off))).normalize();
    }

    private static Vec3 sideOf(Vec3 axis) {
        Vec3 reference = Math.abs(axis.y) > NEAR_VERTICAL ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return axis.cross(reference).normalize();
    }

    private static Optional<WindStep> windOf(String abilityId) {
        AbilitySyncHandler.ClientAbility ability = AbilitySyncHandler.findAbility(abilityId);
        if (ability == null) {
            return Optional.empty();
        }
        return ability.behaviors().stream().flatMap(WindLines::withDescendants)
                .filter(WindStep.class::isInstance).map(WindStep.class::cast).findFirst();
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(WindLines::withDescendants));
    }

    /**
     * The color a line draws at a point of its trail: its blend of its wind
     * step's light and dark colors, fading toward its tail and, for an
     * outward line, out as it ages.
     *
     * @param line the line
     * @param life the share of its life it has lived, 0 to 1
     * @param tail the share of the way from its head to its tail, 0 to 1
     * @return the ARGB color
     */
    static int trailColor(Line line, float life, float tail) {
        float alpha = (1f - fadeOf(line, life)) * (1f - tail);
        return ARGB.color(Math.round(alpha * MAX_ALPHA),
                ARGB.srgbLerp(line.gray(), line.colors().light(), line.colors().dark()));
    }

    /** Drops every line, as a disconnect does. */
    public void clear() {
        live.clear();
    }

    /**
     * Drops the spent lines, drops this tick's snowflakes along the rest and
     * hands the shared path their draw, sorted by the rider they stream past.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the lines' draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        CLIENT.live.removeIf(line -> now - line.startTick() >= lifeOf(line));
        if (CLIENT.live.isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        Vec3 rider = mc.player == null ? Vec3.ZERO : mc.player.getPosition(partialTick);
        List<Line> live = List.copyOf(CLIENT.live);
        draws.accept(new SortedDraw(rider.distanceToSqr(camera),
                () -> drawLines(mc, frame, live, new LinesAt(now + partialTick, camera))));
        CLIENT.dropAlongLines(mc, now);
    }

    /**
     * Where this frame's lines draw from.
     *
     * @param gameTime the game time this frame, partial tick included
     * @param camera   the camera's place in the world
     */
    private record LinesAt(double gameTime, Vec3 camera) {
    }

    private static void drawLines(Minecraft mc, LevelFrame frame, List<Line> live, LinesAt at) {
        Vec3 blob = CastOrigin.ofLocal(mc);
        ImmediateBuffers buffers = ImmediateBuffers.shared();
        RenderType type = RenderTypes.linesTranslucent();
        LineContext lines = new LineContext(frame.poseStack().last(), buffers.getBuffer(type));
        for (Line line : live) {
            double age = at.gameTime() - line.startTick();
            drawLine(lines, at.camera(), line, age, followShift(line, age, blob));
        }
        buffers.endBatch(type);
    }

    /**
     * How far a line blown from the lifted blob shifts off where it was blown:
     * at its start it stands at the blob where the blob stands now, easing to
     * where it was blown over its first tick, after which it flies free, so a
     * moving caster's lines leave the blob rather than the point it was blown at.
     * An inward line stands about the blob where it stands now all its life,
     * so its head lands on the blob however the caster moves.
     * every-ability-casts-from-the-lifted-palm-blob
     * operator ruling: "the cold spray from the blob has the same problem that the decay and mycosis sprays had"
     * operator ruling: each streak's end follows the blob where it stands now
     *
     * @param line    the line
     * @param age     ticks since it was blown
     * @param blobNow where the caster's lifted blob stands this frame
     * @return the shift to add to its path, none for a line not from the blob or an outward one past its first tick
     */
    static Vec3 followShift(Line line, double age, Vec3 blobNow) {
        if (!line.followsTheBlob()) {
            return Vec3.ZERO;
        }
        Vec3 toBlob = blobNow.subtract(line.origin());
        if (line.inward()) {
            return toBlob;
        }
        return age >= 1 ? Vec3.ZERO : toBlob.scale(1 - Math.max(0, age));
    }

    static void drawLine(LineContext lines, Vec3 camera, Line line, double age, Vec3 anchor) {
        float life = (float) (age / LIFE_TICKS);
        double lag = tailLagOf(line, age);
        Vec3 previous = anchor.add(pathPoint(line, age));
        for (int sample = 1; sample <= TAIL_SAMPLES; sample++) {
            float tail = (float) sample / TAIL_SAMPLES;
            Vec3 next = anchor.add(pathPoint(line, age - tail * lag));
            lines.emitPolyline(camera, new Vec3[] {previous, next}, trailColor(line, life, tail), LINE_WIDTH);
            previous = next;
        }
    }

    /**
     * Drops this tick's snowflakes along the lines carrying them.
     *
     * @param mc    the client
     * @param now   the game time
     */
    private void dropAlongLines(Minecraft mc, long now) {
        if (snowflakesDroppedAt == now || mc.level == null) {
            return;
        }
        snowflakesDroppedAt = now;
        RandomSource random = mc.level.getRandom();
        for (Line line : live) {
            if (line.snowflakes() && random.nextFloat() < SNOWFLAKES_PER_LINE_TICK) {
                double age = now - line.startTick();
                Vec3 at = pathPoint(line, age);
                Vec3 along = at.subtract(pathPoint(line, age - 1));
                mc.level.addParticle(GooParticles.SNOWFLAKE.get(), at.x, at.y, at.z, along.x, along.y, along.z);
            }
        }
    }

    /**
     * Flies this held tick's void motes loose from the stream's cone into the
     * blob, each homing on the blob where it stands now.
     * vacuum-motes-fly-loose-from-the-cone-to-the-blob
     *
     * @param mc     the client
     * @param random the random source
     * @param gust   the stream's cone
     */
    private static void flyLooseMotes(Minecraft mc, RandomSource random, Gust gust) {
        for (int mote = 0; mote < MOTES_PER_TICK; mote++) {
            Vec3 at = moteStart(gust, random);
            if (mc.particleEngine.createParticle(GooParticles.VOID_MOTE.get(), at.x, at.y, at.z, 0, 0, 0)
                    instanceof VoidMoteParticle flying) {
                flying.flyInto(() -> CastOrigin.ofLocal(mc));
            }
        }
    }
}
