package com.mercuriusxeno.goo.throwing;

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
    private static final Vec3 PULL = new Vec3(0, -GRAVITY, 0);
    /** The living near a drip that strikes no living thing: none. */
    public static final LivingNear NO_LIVING = (from, to) -> List.of();

    private DripArc() {
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
        Vec3 velocity = launched.add(PULL).scale(DRAG);
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
        Step step = step(position, velocity, clip, airspace);
        while (!step.ended()) {
            step = step(step.position(), step.velocity(), clip, airspace);
        }
        return step;
    }
}
