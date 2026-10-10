package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.WindStep;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

/**
 * Line-drawn wind for a held stream that names it: each held tick a few
 * thick lines, white and very light gray tinted slightly blue, rush straight
 * out of the glove along the cone, swaying slightly off course, then at their
 * end curl out from the cone in a tight spiral facing the player, the head slowing as it winds inward to a center point while
 * the tail draws in quickly behind it, so the line ends at that point as it
 * fades; drawn as smooth curves, and where the stream asks, snowflakes flit
 * weightlessly along them. Typhoon's streams reuse the lines without the
 * snowflakes.
 * cold-streams-wind-lines-and-snowflakes
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
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
    private static final float WIND_PITCH = 1.0f;
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
    /** The curl's starting radius in blocks, which it winds inward from to nothing. */
    static final double CURL_RADIUS = 0.25;
    /** Turns the curl winds through before it reaches its center. */
    static final double CURL_TURNS = 2.0;
    /** Ticks of the path the trailing line spans behind its head while it rushes straight. */
    static final double TAIL_TICKS = 10;
    /** Points the line draws through, enough that its curve reads smooth. */
    private static final int TAIL_SAMPLES = 32;
    private static final float LINE_WIDTH = 3.5f;
    /** Snowflakes a line drops along itself each tick it lives. */
    private static final float SNOWFLAKES_PER_LINE_TICK = 0.2f;
    private static final int MAX_ALPHA = 200;
    private static final int WHITE = 0xF4F8FF;
    private static final int PALE_GRAY = 0xD6DDE8;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;
    private static final double NEAR_VERTICAL = 0.99;
    /** How far behind the eye a jet's tailwind leaves, in blocks, so its lines overtake the camera. */
    static final double TAILWIND_SETBACK = 0.6;

    /** How far a line tilts its curl forward from facing the player, so the head flows into it, in radians. */
    static final double CURL_TILT = 0.35;
    /** The two ways a curl winds. */
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
     * @param origin     where it leaves: in the world, or relative to the player's feet when carried
     * @param axis       the unit direction it rushes along
     * @param outward    the unit direction out from the cone's middle, square to the look, it curls toward
     * @param across     the unit direction square to the look and to outward, which with outward faces the player
     * @param straight   blocks it rushes straight before curling
     * @param sway       how it sways off its course
     * @param winding    which way its curl winds, 1 or -1
     * @param gray       how far its color leans from white to pale gray, 0 to 1
     * @param snowflakes whether snowflakes flit along it
     * @param startTick  the game time it left the glove
     * @param carried    whether it rides along with the player, a jet's tailwind
     */
    record Line(Vec3 origin, Vec3 axis, Vec3 outward, Vec3 across, double straight, Sway sway, double winding,
                float gray, boolean snowflakes, long startTick, boolean carried) {
    }

    private final List<Line> live = new ArrayList<>();
    /** The stream's continuous wind while it plays. */
    private final AtomicReference<StreamWind> wind = new AtomicReference<>();
    /** The game time the stream was last held, which its wind outlasts by WIND_LINGER_TICKS. */
    private long windHeldAt;
    /** The game time snowflakes last dropped, so a tick drawn over several frames drops them once. */
    private long snowflakesDroppedAt = -1;

    private WindLines() {
    }

    /**
     * Where a line's head stands a number of ticks after it left the glove:
     * along its axis for its straight run, then winding inward on a tight
     * curl out from the cone, in a plane that faces back along the look,
     * tilted forward, slowing as it closes on the curl's center, which it
     * reaches the tick the line ends; all the while swaying slightly off course.
     *
     * @param line the line
     * @param age  ticks since it left the glove
     * @return the head's world position
     */
    static Vec3 pathPoint(Line line, double age) {
        double clamped = Math.clamp(age, 0, LIFE_TICKS);
        return coursePoint(line, clamped).add(swayAt(line, clamped));
    }

    private static Vec3 coursePoint(Line line, double age) {
        if (age <= STRAIGHT_TICKS) {
            return line.origin().add(line.axis().scale(line.straight() * launched(age / STRAIGHT_TICKS)));
        }
        double curled = (age - STRAIGHT_TICKS) / (LIFE_TICKS - STRAIGHT_TICKS);
        // the head slows as it winds in: its turn eases out toward the center
        double eased = 1 - (1 - curled) * (1 - curled);
        double angle = eased * CURL_TURNS * TWO_PI;
        double radius = CURL_RADIUS * (1 - eased);
        return curlCenter(line).add(line.outward().scale(-radius * Math.cos(angle)))
                .add(curlForward(line).scale(radius * Math.sin(angle)));
    }

    /**
     * The point a line's curl winds in to: a curl's radius past the end of its
     * straight run, out from the cone's middle.
     *
     * @param line the line
     * @return the curl's center, before the line's sway
     */
    static Vec3 curlCenter(Line line) {
        return line.origin().add(line.axis().scale(line.straight())).add(line.outward().scale(CURL_RADIUS));
    }

    /**
     * The curl plane's second direction beside outward: across the look,
     * tilted forward along the line so the head flows into the curl, winding
     * whichever way the line winds.
     *
     * @param line the line
     * @return the unit direction
     */
    static Vec3 curlForward(Line line) {
        return line.across().scale(line.winding() * Math.cos(CURL_TILT)).add(line.axis().scale(Math.sin(CURL_TILT)))
                .normalize();
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
        double growth = Math.min(1, age / STRAIGHT_TICKS);
        return sway.first().scale(Math.sin(sway.firstRate() * age + sway.firstPhase()) * growth)
                .add(sway.second().scale(Math.sin(sway.secondRate() * age + sway.secondPhase()) * growth));
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
     * Blows this held tick's wind for the local player's stream when its ability names wind.
     *
     * @param player    the streaming player
     * @param abilityId the streamed ability
     * @param area      the ability's area, whose size and angle the wind fills
     * @param apex      the glove hand the stream leaves from
     */
    public static void blow(Player player, String abilityId, AbilityArea area, Vec3 apex) {
        windOf(abilityId).ifPresent(wind -> {
            Gust gust = wind.tailwind()
                    .map(tailwind -> tailwindGust(tailwind, player.getEyeHeight(), player.getLookAngle()))
                    .orElseGet(() -> new Gust(apex, player.getLookAngle(), area.size(), area.angle(), false));
            CLIENT.add(player.level().getRandom(), gust, wind.snowflakes(), player.level().getGameTime());
            CLIENT.keepWindBlowing(player);
        });
    }

    /**
     * Where a held tick's wind blows from and along.
     *
     * @param origin      where the lines leave: in the world, or relative to the player's feet when carried
     * @param axis        the unit direction they rush along
     * @param range       how far the cone they fill reaches, in blocks
     * @param coneDegrees the cone's apex angle, in degrees
     * @param carried     whether the lines ride along with the player
     */
    record Gust(Vec3 origin, Vec3 axis, double range, double coneDegrees, boolean carried) {
    }

    /**
     * A jet's tailwind: the lines ride along with the player, leaving just
     * behind its eye and rushing forward along the look past the camera
     * through the tailwind's cone, so a first-person player sees them overtake
     * it and curl ahead.
     * jet-pushes-along-the-look-while-held
     *
     * @param tailwind  the wind step's tailwind
     * @param eyeHeight the player's eye height
     * @param look      the player's look, unit length
     * @return the gust, its origin relative to the player's feet
     */
    static Gust tailwindGust(WindStep.Tailwind tailwind, double eyeHeight, Vec3 look) {
        Vec3 origin = new Vec3(0, eyeHeight, 0).subtract(look.scale(TAILWIND_SETBACK));
        return new Gust(origin, look, tailwind.range(), tailwind.coneDegrees(), true);
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
            this.pitch = WIND_PITCH;
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

    private void add(RandomSource random, Gust gust, boolean snowflakes, long now) {
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
        double straight = range * STRAIGHT_SHARE * (HALF + random.nextDouble());
        double winding = random.nextBoolean() ? CLOCKWISE : COUNTERCLOCKWISE;
        live.add(new Line(apex, axis, outward, across, straight, sway(random, axis), winding, random.nextFloat(),
                snowflakes, now, gust.carried()));
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
     * The color a line draws at a point of its trail: its blend of white and
     * pale gray, fading toward its tail and out as it ages.
     *
     * @param line the line
     * @param life the share of its life it has lived, 0 to 1
     * @param tail the share of the way from its head to its tail, 0 to 1
     * @return the ARGB color
     */
    static int trailColor(Line line, float life, float tail) {
        float alpha = (1f - life) * (1f - tail);
        return ARGB.color(Math.round(alpha * MAX_ALPHA), ARGB.srgbLerp(line.gray(), WHITE, PALE_GRAY));
    }

    /** Drops every line, as a disconnect does. */
    public void clear() {
        live.clear();
    }

    /**
     * Drops the spent lines, drops this tick's snowflakes along the rest and
     * draws them.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        CLIENT.live.removeIf(line -> now - line.startTick() >= LIFE_TICKS);
        if (CLIENT.live.isEmpty()) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double gameTime = now + partialTick;
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        Vec3 rider = mc.player == null ? Vec3.ZERO : mc.player.getPosition(partialTick);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderTypes.linesTranslucent();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(type));
        for (Line line : CLIENT.live) {
            drawLine(lines, camera, line, gameTime - line.startTick(), anchorOf(line, rider));
        }
        buffers.endBatch(type);
        CLIENT.dropSnowflakes(mc.level, now, mc.player == null ? Vec3.ZERO : mc.player.position());
    }

    /**
     * What a line's path is measured from: the player it rides along with
     * for a carried line, the world's origin for any other.
     * jet-pushes-along-the-look-while-held
     *
     * @param line  the line
     * @param rider the local player's position
     * @return the anchor its path points add to
     */
    static Vec3 anchorOf(Line line, Vec3 rider) {
        return line.carried() ? rider : Vec3.ZERO;
    }

    private static void drawLine(LineContext lines, Vec3 camera, Line line, double age, Vec3 anchor) {
        float life = (float) (age / LIFE_TICKS);
        double lag = tailLag(age);
        Vec3 previous = anchor.add(pathPoint(line, age));
        for (int sample = 1; sample <= TAIL_SAMPLES; sample++) {
            float tail = (float) sample / TAIL_SAMPLES;
            Vec3 next = anchor.add(pathPoint(line, age - tail * lag));
            lines.emitPolyline(camera, new Vec3[] {previous, next}, trailColor(line, life, tail), LINE_WIDTH);
            previous = next;
        }
    }

    private void dropSnowflakes(ClientLevel level, long now, Vec3 rider) {
        if (snowflakesDroppedAt == now) {
            return;
        }
        snowflakesDroppedAt = now;
        RandomSource random = level.getRandom();
        for (Line line : live) {
            if (line.snowflakes() && random.nextFloat() < SNOWFLAKES_PER_LINE_TICK) {
                double age = now - line.startTick();
                Vec3 at = anchorOf(line, rider).add(pathPoint(line, age));
                Vec3 along = at.subtract(anchorOf(line, rider).add(pathPoint(line, age - 1)));
                level.addParticle(GooParticles.SNOWFLAKE.get(), at.x, at.y, at.z, along.x, along.y, along.z);
            }
        }
    }
}
