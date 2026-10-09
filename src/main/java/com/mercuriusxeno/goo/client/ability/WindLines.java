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
import java.util.stream.Stream;

/**
 * Line-drawn wind for a held stream that names it: each held tick a few
 * thick lines, white and very light gray tinted slightly blue, rush straight
 * out of the glove along the cone, then at their end curl up, down or out in
 * a tight spiral, the head slowing as it winds inward to a center point while
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

    /** Lines a held tick blows. */
    static final int LINES_PER_TICK = 2;
    /** Ticks a line lives, from leaving the glove to fading out. */
    static final int LIFE_TICKS = 18;
    /** The share of a line's life it rushes straight before it curls. */
    static final double CURL_STARTS = 0.6;
    /** The ticks a line rushes straight before it curls. */
    static final double STRAIGHT_TICKS = LIFE_TICKS * CURL_STARTS;
    /** The share of the cone's length a line rushes straight before it curls. */
    static final double STRAIGHT_SHARE = 0.6;
    /** The curl's starting radius in blocks, which it winds inward from to nothing. */
    static final double CURL_RADIUS = 0.35;
    /** Turns the curl winds through before it reaches its center. */
    static final double CURL_TURNS = 1.5;
    /** Ticks of the path the trailing line spans behind its head while it rushes straight. */
    static final double TAIL_TICKS = 5;
    /** Points the line draws through, enough that its curve reads smooth. */
    private static final int TAIL_SAMPLES = 32;
    private static final float LINE_WIDTH = 3.5f;
    /** Snowflakes a line drops along itself each tick it lives. */
    private static final float SNOWFLAKES_PER_LINE_TICK = 0.35f;
    private static final int MAX_ALPHA = 200;
    private static final int WHITE = 0xF4F8FF;
    private static final int PALE_GRAY = 0xD6DDE8;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;
    private static final double NEAR_VERTICAL = 0.99;

    /** Where a spiralling line drifts as it fades. */
    enum Drift {
        UP, DOWN, OUT
    }

    /**
     * One line of wind.
     *
     * @param origin     where it leaves the glove
     * @param axis       the unit direction it rushes along
     * @param side       a unit direction square to the axis
     * @param up         the unit direction square to both
     * @param straight   blocks it rushes straight before spiralling
     * @param drift      where it drifts once spiralling
     * @param phase      the spiral's starting angle
     * @param gray       how far its color leans from white to pale gray, 0 to 1
     * @param snowflakes whether snowflakes flit along it
     * @param startTick  the game time it left the glove
     */
    record Line(Vec3 origin, Vec3 axis, Vec3 side, Vec3 up, double straight, Drift drift, double phase, float gray,
                boolean snowflakes, long startTick) {
    }

    private final List<Line> live = new ArrayList<>();
    /** The game time snowflakes last dropped, so a tick drawn over several frames drops them once. */
    private long snowflakesDroppedAt = -1;

    private WindLines() {
    }

    /**
     * Where a line's head stands a number of ticks after it left the glove:
     * along its axis for its straight run, then winding inward on a tight
     * curl that turns up, down or out, slowing as it closes on the curl's
     * center, which it reaches the tick the line ends.
     *
     * @param line the line
     * @param age  ticks since it left the glove
     * @return the head's world position
     */
    static Vec3 pathPoint(Line line, double age) {
        double clamped = Math.clamp(age, 0, LIFE_TICKS);
        if (clamped <= STRAIGHT_TICKS) {
            return line.origin().add(line.axis().scale(line.straight() * clamped / STRAIGHT_TICKS));
        }
        double curled = (clamped - STRAIGHT_TICKS) / (LIFE_TICKS - STRAIGHT_TICKS);
        // the head slows as it winds in: its turn eases out toward the center
        double eased = 1 - (1 - curled) * (1 - curled);
        double angle = eased * CURL_TURNS * TWO_PI;
        double radius = CURL_RADIUS * (1 - eased);
        Vec3 outward = curlDirection(line);
        Vec3 center = curlCenter(line);
        return center.add(outward.scale(-radius * Math.cos(angle))).add(line.axis().scale(radius * Math.sin(angle)));
    }

    /**
     * The point a line's curl winds in to: a curl's radius past the end of its
     * straight run, toward the way it curls.
     *
     * @param line the line
     * @return the curl's center
     */
    static Vec3 curlCenter(Line line) {
        return line.origin().add(line.axis().scale(line.straight())).add(curlDirection(line).scale(CURL_RADIUS));
    }

    /**
     * The way a line curls: up, down, or out from the stream's middle.
     *
     * @param line the line
     * @return the unit direction square to its axis
     */
    private static Vec3 curlDirection(Line line) {
        return switch (line.drift()) {
            case UP -> line.up();
            case DOWN -> line.up().reverse();
            case OUT -> line.side().scale(Math.cos(line.phase())).add(line.up().scale(Math.sin(line.phase())));
        };
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
        windOf(abilityId).ifPresent(wind -> CLIENT.add(player.level().getRandom(), apex, player.getLookAngle(),
                area.size(), area.angle(), wind.snowflakes(), player.level().getGameTime()));
    }

    private void add(RandomSource random, Vec3 apex, Vec3 look, double range, double coneDegrees, boolean snowflakes,
                     long now) {
        double spread = Math.toRadians(coneDegrees * HALF);
        for (int i = 0; i < LINES_PER_TICK; i++) {
            Vec3 axis = tilt(look, spread * Math.sqrt(random.nextDouble()), random.nextDouble() * TWO_PI);
            Vec3 side = sideOf(axis);
            Vec3 up = axis.cross(side).normalize();
            double straight = range * STRAIGHT_SHARE * (HALF + random.nextDouble());
            live.add(new Line(apex, axis, side, up, straight, Drift.values()[random.nextInt(Drift.values().length)],
                    random.nextDouble() * TWO_PI, random.nextFloat(), snowflakes, now));
        }
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
        Vec3 side = sideOf(look);
        Vec3 up = look.cross(side).normalize();
        Vec3 sway = side.scale(Math.cos(about)).add(up.scale(Math.sin(about)));
        return look.scale(Math.cos(off)).add(sway.scale(Math.sin(off))).normalize();
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
        double gameTime = now + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderTypes.linesTranslucent();
        LineContext lines = new LineContext(event.getPoseStack().last(), buffers.getBuffer(type));
        for (Line line : CLIENT.live) {
            drawLine(lines, camera, line, gameTime - line.startTick());
        }
        buffers.endBatch(type);
        CLIENT.dropSnowflakes(mc.level, now);
    }

    private static void drawLine(LineContext lines, Vec3 camera, Line line, double age) {
        float life = (float) (age / LIFE_TICKS);
        double lag = tailLag(age);
        Vec3 previous = pathPoint(line, age);
        for (int sample = 1; sample <= TAIL_SAMPLES; sample++) {
            float tail = (float) sample / TAIL_SAMPLES;
            Vec3 next = pathPoint(line, age - tail * lag);
            lines.emitPolyline(camera, new Vec3[] {previous, next}, trailColor(line, life, tail), LINE_WIDTH);
            previous = next;
        }
    }

    private void dropSnowflakes(ClientLevel level, long now) {
        if (snowflakesDroppedAt == now) {
            return;
        }
        snowflakesDroppedAt = now;
        RandomSource random = level.getRandom();
        for (Line line : live) {
            if (line.snowflakes() && random.nextFloat() < SNOWFLAKES_PER_LINE_TICK) {
                double age = now - line.startTick();
                Vec3 at = pathPoint(line, age);
                Vec3 along = at.subtract(pathPoint(line, age - 1));
                level.addParticle(GooParticles.SNOWFLAKE.get(), at.x, at.y, at.z, along.x, along.y, along.z);
            }
        }
    }
}
