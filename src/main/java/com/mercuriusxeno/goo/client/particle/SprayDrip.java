package com.mercuriusxeno.goo.client.particle;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * One Goop or Slick spray drip's flight: thrown from the glove, it arcs at a
 * steady pace onto the end of its coat ray and splats there; only its color
 * is rolled. It keeps its caster's full travel over its opening ticks, so it
 * leaves with the hand of a caster on the move, then a pull bends the travel
 * back out, so a running or jumping caster's drips still land on the coat.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * goop-and-slick-spray-follows-the-coat
 * glove-stream-motes-carry-the-players-travel-velocity
 *
 * @param rgb         the drip's color
 * @param velocity    the drip's velocity at launch, blocks per tick
 * @param travelPull  the change the bend makes in the drip's velocity each tick after its opening ticks
 * @param holdTicks   the opening ticks the drip keeps its caster's full travel
 * @param flightTicks the ticks the drip flies before it splats
 */
public record SprayDrip(int rgb, Vec3 velocity, Vec3 travelPull, int holdTicks, int flightTicks) {

    /** The opening ticks a drip keeps its caster's full travel, the operator's ruling: hold, then bend. */
    static final int HOLD_TICKS = 3;
    /** The drip's pace along the line to its end, blocks per tick. */
    private static final double CRUISE_SPEED = 0.6;
    /** The arc's gravity, blocks per tick per tick; a drip flying eight blocks peaks about half a block up. */
    private static final double ARC_GRAVITY = 0.02;
    private static final Vec3 ARC_PULL = new Vec3(0, -ARC_GRAVITY, 0);
    private static final double HALF = 0.5;
    private static final double TWICE = 2.0;
    /** The fewest ticks a bend needs to take a travel back out. */
    private static final int BEND_TICKS = 2;

    /**
     * Throws a drip in a palette's colors onto its coat ray's end.
     *
     * @param apex    the glove the drip leaves
     * @param end     the end of the drip's coat ray, where it splats
     * @param travel  the caster's travel velocity, blocks per tick
     * @param palette the colors the drip blends between
     * @param random  the source the drip's color is rolled from
     * @return the drip
     */
    static SprayDrip launch(Vec3 apex, Vec3 end, Vec3 travel, BlendPalette palette, RandomSource random) {
        return toward(apex, end, travel, palette.colorAt(random.nextFloat()));
    }

    /**
     * Plans a drip's flight onto its end: the ticks its pace needs, a launch
     * lifted for its arc and carrying the caster's travel, and the bend that
     * takes the travel back out once the opening ticks are flown. A flight
     * too short to bend carries no travel.
     *
     * @param apex   the glove the drip leaves
     * @param end    where the drip splats
     * @param travel the caster's travel velocity, blocks per tick
     * @param rgb    the drip's color
     * @return the drip
     */
    static SprayDrip toward(Vec3 apex, Vec3 end, Vec3 travel, int rgb) {
        return plan(apex, end, travel, rgb, HOLD_TICKS);
    }

    private static SprayDrip plan(Vec3 apex, Vec3 end, Vec3 travel, int rgb, int holdWanted) {
        Vec3 gap = end.subtract(apex);
        int ticks = Math.max(1, (int) Math.ceil(gap.length() / CRUISE_SPEED));
        Vec3 velocity = gap.scale(1.0 / ticks).subtract(ARC_PULL.scale((ticks - 1) * HALF));
        if (ticks < BEND_TICKS) {
            return new SprayDrip(rgb, velocity, Vec3.ZERO, 0, ticks);
        }
        int hold = Math.min(holdWanted, ticks - BEND_TICKS);
        int bend = ticks - hold;
        Vec3 travelPull = travel.scale(-TWICE * ticks / (bend * (bend - 1.0)));
        return new SprayDrip(rgb, velocity.add(travel), travelPull, hold, ticks);
    }

    /**
     * Where a drip the glove still holds stands: where its own flight would
     * have taken it had the caster kept still, carried with the glove as it
     * has moved and turned since launch, so the drip leaves the hand however
     * the caster walks or turns.
     *
     * @param stillAt  where the drip would stand had the caster kept still
     * @param atLaunch the caster's pose when the drip was thrown
     * @param now      the caster's pose now
     * @return where the drip stands
     */
    static Vec3 heldAt(Vec3 stillAt, GloveSwing.CasterPose atLaunch, GloveSwing.CasterPose now) {
        return now.glove().add(GloveSwing.turned(stillAt.subtract(atLaunch.glove()), atLaunch.look(), now.look()));
    }

    /**
     * Plans the flight of a drip the glove lets go of: it leaves with the
     * glove's velocity, which the pull bends straight back out, and arcs onto
     * its end.
     *
     * @param from          where the drip stands as the glove lets go
     * @param end           where the drip splats
     * @param gloveVelocity the glove's velocity as it lets go, blocks per tick
     * @param rgb           the drip's color
     * @return the drip
     */
    static SprayDrip released(Vec3 from, Vec3 end, Vec3 gloveVelocity, int rgb) {
        return plan(from, end, gloveVelocity, rgb, 0);
    }

    /**
     * One tick of the drip's flight: it moves by its velocity, then the arc
     * pulls the velocity down, and past the opening ticks the bend pulls the
     * travel back out.
     *
     * @param position where the drip stands
     * @param velocity the drip's velocity, blocks per tick
     * @param flown    the ticks the drip has flown
     * @return where the drip stands after the tick, and whether it splats there
     */
    Step step(Vec3 position, Vec3 velocity, int flown) {
        Vec3 pulled = velocity.add(ARC_PULL);
        return new Step(position.add(velocity), flown >= holdTicks ? pulled.add(travelPull) : pulled,
                flown + 1 >= flightTicks);
    }

    /**
     * Where a tick of flight leaves a drip.
     *
     * @param position where the drip stands
     * @param velocity the drip's velocity, blocks per tick
     * @param splats   whether the drip has reached its end, splatting there and leaving the world
     */
    record Step(Vec3 position, Vec3 velocity, boolean splats) {
    }
}
