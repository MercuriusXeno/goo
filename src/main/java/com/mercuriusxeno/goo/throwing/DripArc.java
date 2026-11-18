package com.mercuriusxeno.goo.throwing;

import com.mercuriusxeno.goo.lunar.LunarGravity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;

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
    /** The step a throw's flight times are tried at, in ticks. */
    private static final double THROW_SCAN_TICKS = 0.25;
    /** The flight times a throw onto a point tries, out to 200 ticks. */
    private static final int THROW_TRIES = 800;
    /** The halvings that close in on the flight time a throw just reaches its point over. */
    private static final int FLIGHT_TIME_HALVINGS = 40;
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
     * A drip's launch onto a point at its exit speed: the lowest arc, under
     * the gravity and the drag it flies through, that reaches the point, or
     * the arc passing nearest it where none reaches it.
     * residual-spray-cone-centers-on-the-look-from-the-blob: each drip flies a plain arc from the blob to its spot
     *
     * @param from      where the drip leaves
     * @param target    the point it is thrown onto
     * @param exitSpeed its exit speed, blocks per tick
     * @param gravity   the pull on it each tick
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 launchOnto(Vec3 from, Vec3 target, double exitSpeed, double gravity) {
        Vec3 line = target.subtract(from);
        double nearest = 0;
        double nearestShortfall = Double.MAX_VALUE;
        double before = 0;
        for (int tried = 1; tried <= THROW_TRIES; tried++) {
            double ticks = tried * THROW_SCAN_TICKS;
            double shortfall = shortfallOver(line, ticks, exitSpeed, gravity);
            if (shortfall <= 0) {
                return aimedOver(line, flightTimeBetween(line, before, ticks, exitSpeed, gravity), exitSpeed, gravity);
            }
            if (shortfall < nearestShortfall) {
                nearestShortfall = shortfall;
                nearest = ticks;
            }
            before = ticks;
        }
        return aimedOver(line, nearest, exitSpeed, gravity);
    }

    /**
     * The flight time, between one too short and one long enough, over which a throw just reaches the point.
     *
     * @param line      from where the drip leaves to the point
     * @param tooShort  a flight time the throw falls short over
     * @param enough    a flight time the throw reaches the point within
     * @param exitSpeed the drip's exit speed, blocks per tick
     * @param gravity   the pull on it each tick
     * @return the flight time, in ticks
     */
    private static double flightTimeBetween(Vec3 line, double tooShort, double enough, double exitSpeed,
                                            double gravity) {
        double low = tooShort;
        double high = enough;
        for (int halving = 0; halving < FLIGHT_TIME_HALVINGS; halving++) {
            double middle = (low + high) * HALF;
            if (shortfallOver(line, middle, exitSpeed, gravity) > 0) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return high;
    }

    /**
     * The launch whose arc stands on the point after a flight time: aimed
     * above the point by what gravity pulls the drip down over that time.
     *
     * @param line      from where the drip leaves to the point
     * @param ticks     the flight time, in ticks
     * @param exitSpeed the drip's exit speed, blocks per tick
     * @param gravity   the pull on it each tick
     * @return the launch velocity, blocks per tick
     */
    private static Vec3 aimedOver(Vec3 line, double ticks, double exitSpeed, double gravity) {
        Vec3 aim = line.add(0, gravity * fallOver(ticks), 0);
        return aim.lengthSqr() == 0 ? new Vec3(0, -exitSpeed, 0) : aim.normalize().scale(exitSpeed);
    }

    /**
     * How far short of the point a throw at the exit speed falls over a
     * flight time: positive while it falls short, at most zero once it reaches.
     *
     * @param line      from where the drip leaves to the point
     * @param ticks     the flight time, in ticks
     * @param exitSpeed the drip's exit speed, blocks per tick
     * @param gravity   the pull on it each tick
     * @return the shortfall, in blocks
     */
    private static double shortfallOver(Vec3 line, double ticks, double exitSpeed, double gravity) {
        return line.add(0, gravity * fallOver(ticks), 0).length() - exitSpeed * carriedOver(ticks);
    }

    /**
     * How far a launch velocity of one block a tick carries a drip over a
     * flight time, drag slowing it each tick: the sum of drag's powers.
     *
     * @param ticks the flight time, in ticks
     * @return the distance carried, in blocks
     */
    private static double carriedOver(double ticks) {
        return DRAG * (1 - Math.pow(DRAG, ticks)) / (1 - DRAG);
    }

    /**
     * How far a pull of one block a tick per tick drags a drip down over a flight time.
     *
     * @param ticks the flight time, in ticks
     * @return the fall, in blocks
     */
    private static double fallOver(double ticks) {
        return DRAG / (1 - DRAG) * (ticks - carriedOver(ticks));
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
        Vec3 velocity = launched.add(0, -flight.gravity(), 0).scale(DRAG);
        return moveAlong(position, position.add(velocity), velocity, flight.clip(), flight.living(),
                flight.airspace());
    }

    /**
     * Where a tick's segment leaves a thing in flight: the nearest of the
     * living boxes and the blocks the segment meets ends it, and a segment
     * leaving the airspace loses it.
     *
     * @param position where the tick starts
     * @param next     where the segment ends unstruck
     * @param velocity the velocity the next tick flies by
     * @param clip     the blocks the segment may meet
     * @param living   the living the segment may meet
     * @param airspace where it may fly
     * @return where the tick leaves it
     */
    static Step moveAlong(Vec3 position, Vec3 next, Vec3 velocity, CoatRay.BlockClip clip, LivingNear living,
                          Airspace airspace) {
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
