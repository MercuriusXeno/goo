package com.mercuriusxeno.goo.throwing;

import com.mercuriusxeno.goo.lunar.LunarGravity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * One Goop or Slick spray drip's flight: a drip holds a position and a
 * velocity, and each tick gravity and drag bend the velocity as they bend a
 * thrown projectile's and the drip moves by it, ending on the first block
 * face its tick's segment meets. It has
 * no range: it flies until it strikes, however far that is, and ends
 * unstruck only where it leaves the airspace the world holds, past the
 * build height or into chunks not loaded. The server's drips and the drawn
 * drips step through this one arc.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * goop-and-slick-throw-keeps-todays-level-reach
 * drips-fly-under-thrown-projectile-gravity-and-drag
 */
public final class DripArc {

    /** The pull gravity puts on a drip's velocity each tick, a thrown projectile's, blocks per tick per tick. */
    public static final double GRAVITY = 0.03;
    /** The share of a drip's velocity air keeps each tick, a thrown projectile's inertia out of water. */
    public static final double DRAG = 0.99F;
    /** The living near a drip that strikes no living thing: none. */
    public static final LivingNear NO_LIVING = (from, to) -> List.of();
    /** Under this a target stands on the apex, or straight over or under it. */
    private static final double MIN_AIM_LENGTH = 1e-3;
    private static final double MIN_AIM_LENGTH_SQUARED = MIN_AIM_LENGTH * MIN_AIM_LENGTH;
    /** The steepest pitch an aimed drip leaves at, just short of straight up. */
    private static final double STEEPEST_PITCH = Math.toRadians(89);
    /** The pitch step the aim climbs by in search of the first arc reaching its target. */
    private static final double PITCH_SCAN_STEP = Math.toRadians(1);
    /** The halvings that pin the aimed pitch inside its step, to well under a thousandth of a degree. */
    private static final int PITCH_BISECTIONS = 20;
    /** The ticks of flight an aim steps before it gives a drip up as never reaching its target. */
    private static final int MOST_AIM_TICKS = 400;
    private static final double HALF = 0.5;

    private DripArc() {
    }

    /**
     * The pull gravity puts on a drip's velocity each tick in a dimension: one sixth of the overworld's in lunar.
     * decision lunar-gravity-is-one-sixth-of-the-overworlds
     *
     * @param dimension the dimension the drip flies in
     * @return the pull, blocks per tick per tick
     */
    public static double gravityIn(ResourceKey<Level> dimension) {
        return GRAVITY * LunarGravity.scaleIn(dimension);
    }

    /**
     * Where a drip may fly: inside the world's build height, in chunks the world holds loaded.
     */
    @FunctionalInterface
    public interface Airspace {

        /**
         * @param point a point the drip reaches
         * @return true where the drip may fly on
         */
        boolean holds(Vec3 point);
    }

    /**
     * A living thing a drip may strike, handed to the arc as its box.
     *
     * @param entityId the living thing's entity id
     * @param box      its bounding box
     */
    public record LivingBox(int entityId, AABB box) {
    }

    /**
     * The living things standing near a tick's segment, the caster aside.
     */
    @FunctionalInterface
    public interface LivingNear {

        /**
         * @param from the segment's start
         * @param to   the segment's end
         * @return the boxes of the living the segment may meet
         */
        List<LivingBox> along(Vec3 from, Vec3 to);
    }


    /**
     * Where one tick of flight leaves a drip.
     *
     * @param position where the drip stands after the tick: the strike point where it struck
     * @param velocity its velocity for the next tick
     * @param strike   the block face the tick's segment met, empty while the drip flies on or struck a living thing
     * @param living   the living thing the tick's segment met before any block, empty where it met none
     * @param lost     whether the drip left the airspace unstruck, ending there
     */
    public record Step(Vec3 position, Vec3 velocity, Optional<CoatRay.BlockStrike> strike,
                       Optional<LivingBox> living, boolean lost) {

        /**
         * @return true once the drip struck a block or a living thing, or left the airspace
         */
        public boolean ended() {
            return strike.isPresent() || living.isPresent() || lost;
        }
    }

    /**
     * A drip's launch velocity for its share of the cone: its heading inside
     * the cone at the throw speed, wherever and however the caster moves.
     * goop-and-slick-drips-add-none-of-the-players-movement
     *
     * @param axis        the cone's axis
     * @param coneDegrees the cone, apex to rim, in degrees
     * @param share       the drip's place in the cone
     * @param throwSpeed  the delivery's throw speed, blocks per tick
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 launch(Vec3 axis, double coneDegrees, CoatRay.ConeShare share, double throwSpeed) {
        return StreamCone.launchDirection(axis, coneDegrees, share.tilt(), share.turn()).scale(throwSpeed);
    }

    /**
     * The launch axis that flies a drip leaving an apex at an exit speed
     * through a target point: the heading points straight at the target
     * across the ground, since gravity pulls only down and drag slows every
     * axis alike, and the pitch is the lowest whose arc, stepped as
     * {@link #step} steps it, stands at the target's height as it passes
     * over the target. A spray opening from the glove hand so lands its
     * center on the crosshair's point rather than beside the sight line.
     * diagnose-and-correct-the-spray-landing-off-the-aim
     *
     * @param apex      where the drip leaves, the glove hand
     * @param target    the point the drip should pass through, where the crosshair lands
     * @param look      the axis where no launch at the exit speed reaches the target
     * @param exitSpeed the drip's exit speed, blocks per tick
     * @return the unit launch axis
     */
    public static Vec3 axisToward(Vec3 apex, Vec3 target, Vec3 look, double exitSpeed) {
        return axisToward(apex, target, look, exitSpeed, GRAVITY);
    }

    /**
     * The launch axis that flies a drip through a target point under a gravity, as {@link #axisToward} finds it.
     *
     * @param apex      where the drip leaves, the glove hand
     * @param target    the point the drip should pass through, where the crosshair lands
     * @param look      the axis where no launch at the exit speed reaches the target
     * @param exitSpeed the drip's exit speed, blocks per tick
     * @param gravity   the pull on the drip each tick, {@link #gravityIn} the dimension it flies in
     * @return the unit launch axis
     */
    public static Vec3 axisToward(Vec3 apex, Vec3 target, Vec3 look, double exitSpeed, double gravity) {
        Vec3 line = target.subtract(apex);
        double across = line.horizontalDistance();
        if (exitSpeed <= 0 || line.lengthSqr() < MIN_AIM_LENGTH_SQUARED) {
            return look;
        }
        if (across < MIN_AIM_LENGTH) {
            // straight over or under the hand, gravity bends no drip off the line
            return line.y > peakOf(exitSpeed, gravity) ? look : line.normalize();
        }
        OptionalDouble pitch = lowestPitchThrough(new Throw(across, line.y, exitSpeed, gravity));
        if (pitch.isEmpty()) {
            return look;
        }
        double cos = Math.cos(pitch.getAsDouble());
        return new Vec3(line.x / across * cos, Math.sin(pitch.getAsDouble()), line.z / across * cos);
    }

    /**
     * A throw an aim solves for: a target across the ground and its height over the launch, at a launch speed
     * under a gravity.
     *
     * @param across    the distance across the ground, blocks
     * @param rise      the height over the launch the arc should stand at there, blocks
     * @param exitSpeed the launch speed, blocks per tick
     * @param gravity   the pull on the drip each tick
     */
    private record Throw(double across, double rise, double exitSpeed, double gravity) {
    }

    /**
     * The lowest launch pitch whose free arc stands at a height as it passes a distance across the ground.
     *
     * @param aim the throw the pitch is solved for
     * @return the pitch, radians above level, empty where no pitch short of straight up reaches
     */
    private static OptionalDouble lowestPitchThrough(Throw aim) {
        // gravity only pulls a drip below its line, so the lowest arc reaching the target leaves above that line
        double low = Math.atan2(aim.rise(), aim.across());
        double high = low;
        while (heightOver(aim, high) < aim.rise()) {
            if (high >= STEEPEST_PITCH) {
                return OptionalDouble.empty();
            }
            low = high;
            high = Math.min(STEEPEST_PITCH, high + PITCH_SCAN_STEP);
        }
        for (int i = 0; i < PITCH_BISECTIONS; i++) {
            double mid = (low + high) * HALF;
            if (heightOver(aim, mid) < aim.rise()) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return OptionalDouble.of(high);
    }

    /**
     * How high a drip thrown straight up climbs before it falls, stepped as {@link #step} steps it.
     *
     * @param exitSpeed the launch speed, blocks per tick
     * @param gravity   the pull on the drip each tick
     * @return the height of its peak over its launch, blocks
     */
    private static double peakOf(double exitSpeed, double gravity) {
        double height = 0;
        double upSpeed = exitSpeed;
        while (true) {
            upSpeed = (upSpeed - gravity) * DRAG;
            if (upSpeed <= 0) {
                return height;
            }
            height += upSpeed;
        }
    }

    /**
     * How high over its launch a free drip stands as it passes the throw's distance across the ground, stepped as
     * {@link #step} steps it with nothing to strike.
     *
     * @param aim   the throw, whose rise is the height under which a falling drip can never climb back to the target
     * @param pitch the launch pitch, radians above level
     * @return the height over the launch as the drip passes, or negative infinity where it falls under the
     *         target's height first or stalls short of it
     */
    private static double heightOver(Throw aim, double pitch) {
        double ground = 0;
        double height = 0;
        double groundSpeed = Math.cos(pitch) * aim.exitSpeed();
        double upSpeed = Math.sin(pitch) * aim.exitSpeed();
        for (int tick = 0; tick < MOST_AIM_TICKS; tick++) {
            groundSpeed *= DRAG;
            upSpeed = (upSpeed - aim.gravity()) * DRAG;
            double nextGround = ground + groundSpeed;
            double nextHeight = height + upSpeed;
            if (nextGround >= aim.across()) {
                return height + (nextHeight - height) * (aim.across() - ground) / (nextGround - ground);
            }
            if (nextHeight < aim.rise() && upSpeed < 0) {
                return Double.NEGATIVE_INFINITY;
            }
            ground = nextGround;
            height = nextHeight;
        }
        return Double.NEGATIVE_INFINITY;
    }

    /**
     * One tick of a drip's flight.
     *
     * @param position where the drip stands
     * @param velocity its velocity, blocks per tick
     * @param clip     the blocks its segment may meet
     * @param airspace where it may fly
     * @return where the tick leaves it
     */
    public static Step step(Vec3 position, Vec3 velocity, CoatRay.BlockClip clip, Airspace airspace) {
        return step(position, velocity, clip, NO_LIVING, airspace);
    }

    /**
     * One tick of a drip's flight that may end on a living thing: gravity
     * pulls the velocity, air drags it, and the drip moves by what is left,
     * the order vanilla's ThrowableProjectile ticks in; of the living boxes
     * and the blocks the tick's segment meets, the nearest ends it.
     * goop-wobbles-a-mob-its-drip-strikes
     * drips-fly-under-thrown-projectile-gravity-and-drag
     *
     * @param position where the drip stands
     * @param launched its velocity before the tick, blocks per tick
     * @param clip     the blocks its segment may meet
     * @param living   the living its segment may meet
     * @param airspace where it may fly
     * @return where the tick leaves it
     */
    public static Step step(Vec3 position, Vec3 launched, CoatRay.BlockClip clip, LivingNear living,
                            Airspace airspace) {
        return step(position, launched, new Flight(clip, living, airspace, GRAVITY));
    }

    /**
     * What a drip's flight runs against: the blocks and the living it may meet, where it may fly, and the gravity
     * pulling it.
     *
     * @param clip     the blocks its segment may meet
     * @param living   the living its segment may meet
     * @param airspace where it may fly
     * @param gravity  the pull on the drip each tick, {@link #gravityIn} the dimension it flies in
     */
    public record Flight(CoatRay.BlockClip clip, LivingNear living, Airspace airspace, double gravity) {
    }

    /**
     * One tick of a drip's flight under its flight's gravity, as {@link #step} steps it.
     *
     * @param position where the drip stands
     * @param launched its velocity before the tick, blocks per tick
     * @param flight   what the flight runs against
     * @return where the tick leaves it
     */
    public static Step step(Vec3 position, Vec3 launched, Flight flight) {
        CoatRay.BlockClip clip = flight.clip();
        LivingNear living = flight.living();
        Airspace airspace = flight.airspace();
        Vec3 velocity = launched.add(0, -flight.gravity(), 0).scale(DRAG);
        Vec3 next = position.add(velocity);
        // the airspace is read before the clip, so no segment reaches into a chunk the world holds unloaded
        if (!airspace.holds(next)) {
            return new Step(next, velocity, Optional.empty(), Optional.empty(), true);
        }
        Optional<CoatRay.BlockStrike> strike = clip.firstHit(position, next);
        double blockReach = strike.map(struck -> position.distanceToSqr(struck.point())).orElse(Double.MAX_VALUE);
        Optional<Step> onTheLiving = nearestLiving(position, next, living, blockReach)
                .map(hit -> new Step(hit.point(), velocity, Optional.empty(), Optional.of(hit.box()), false));
        if (onTheLiving.isPresent()) {
            return onTheLiving.get();
        }
        return strike.map(struck -> new Step(struck.point(), velocity, strike, Optional.empty(), false))
                .orElseGet(() -> new Step(next, velocity, Optional.empty(), Optional.empty(), false));
    }

    /**
     * Where a segment first meets a living box.
     *
     * @param point the point on the box
     * @param box   the box
     */
    private record LivingHit(Vec3 point, LivingBox box) {
    }

    /**
     * The living box a segment meets first, where it meets one nearer than a reach.
     *
     * @param from   the segment's start
     * @param to     the segment's end
     * @param living the living the segment may meet
     * @param within the squared distance from the start the hit must stand nearer than
     * @return the nearest hit, empty where none stands within the reach
     */
    private static Optional<LivingHit> nearestLiving(Vec3 from, Vec3 to, LivingNear living, double within) {
        LivingHit nearest = null;
        double reach = within;
        for (LivingBox box : living.along(from, to)) {
            Optional<Vec3> hit = box.box().contains(from) ? Optional.of(from) : box.box().clip(from, to);
            if (hit.isPresent() && from.distanceToSqr(hit.get()) < reach) {
                nearest = new LivingHit(hit.get(), box);
                reach = from.distanceToSqr(hit.get());
            }
        }
        return Optional.ofNullable(nearest);
    }

    /**
     * Flies a drip from its launch to its end.
     *
     * @param position where the drip leaves
     * @param velocity its launch velocity, blocks per tick
     * @param clip     the blocks it may meet
     * @param airspace where it may fly
     * @return the step that ends it: its strike, or where it left the airspace unstruck
     */
    public static Step flyToItsEnd(Vec3 position, Vec3 velocity, CoatRay.BlockClip clip, Airspace airspace) {
        return flyToItsEnd(position, velocity, new Flight(clip, NO_LIVING, airspace, GRAVITY));
    }

    /**
     * Flies a drip from its launch to its end under its flight's gravity.
     *
     * @param position where the drip leaves
     * @param velocity its launch velocity, blocks per tick
     * @param flight   what the flight runs against
     * @return the step that ends it: its strike, or where it left the airspace unstruck
     */
    public static Step flyToItsEnd(Vec3 position, Vec3 velocity, Flight flight) {
        Step step = step(position, velocity, flight);
        while (!step.ended()) {
            step = step(step.position(), step.velocity(), flight);
        }
        return step;
    }
}
