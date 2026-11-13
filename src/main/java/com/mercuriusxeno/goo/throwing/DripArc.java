package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * One Goop or Slick spray drip's flight: a drip holds a position and a
 * velocity, and each tick it moves by its velocity while gravity bends the
 * velocity, ending on the first block face its tick's segment meets. It has
 * no range: it flies until it strikes, however far that is, and ends
 * unstruck only where it leaves the airspace the world holds, past the
 * build height or into chunks not loaded. The server's drips and the drawn
 * drips step through this one arc.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * goop-and-slick-throw-keeps-todays-level-reach
 */
public final class DripArc {

    /** The pull gravity puts on a drip's velocity each tick, blocks per tick per tick. */
    public static final double GRAVITY = 0.02;
    private static final Vec3 PULL = new Vec3(0, -GRAVITY, 0);

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
     * Where one tick of flight leaves a drip.
     *
     * @param position where the drip stands after the tick: the strike point where it struck
     * @param velocity its velocity for the next tick
     * @param strike   the block face the tick's segment met, empty while the drip flies on
     * @param lost     whether the drip left the airspace unstruck, ending there
     */
    public record Step(Vec3 position, Vec3 velocity, Optional<CoatRay.BlockStrike> strike, boolean lost) {

        /**
         * @return true once the drip struck a block or left the airspace
         */
        public boolean ended() {
            return strike.isPresent() || lost;
        }
    }

    /**
     * A drip's launch velocity for its share of the cone: its heading inside
     * the cone at the throw speed, plus the caster's travel.
     *
     * @param axis        the cone's axis
     * @param coneDegrees the cone, apex to rim, in degrees
     * @param share       the drip's place in the cone
     * @param throwSpeed  the delivery's throw speed, blocks per tick
     * @param travel      the caster's travel velocity, blocks per tick
     * @return the launch velocity, blocks per tick
     */
    public static Vec3 launch(Vec3 axis, double coneDegrees, CoatRay.ConeShare share, double throwSpeed,
                              Vec3 travel) {
        return StreamCone.launchDirection(axis, coneDegrees, share.tilt(), share.turn()).scale(throwSpeed).add(travel);
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
        Vec3 next = position.add(velocity);
        // the airspace is read before the clip, so no segment reaches into a chunk the world holds unloaded
        if (!airspace.holds(next)) {
            return new Step(next, velocity, Optional.empty(), true);
        }
        Optional<CoatRay.BlockStrike> strike = clip.firstHit(position, next);
        return strike.map(struck -> new Step(struck.point(), velocity, strike, false))
                .orElseGet(() -> new Step(next, velocity.add(PULL), Optional.empty(), false));
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
