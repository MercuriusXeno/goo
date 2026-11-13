package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * One Goop or Slick spray drip's flight; only its color is rolled. A Goop
 * drip flies the arc the server's drip flies, from its launch under the same
 * gravity, and splats where its own segment meets a block. A Slick drip,
 * thrown from the glove carrying its caster's travel, arcs at a steady pace
 * onto the end of its coat ray and splats there: a light gravity lifts the
 * arc, and a pull against the travel bends it back out, so a moving
 * caster's drips still land on the coat.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * goop-and-slick-spray-follows-the-coat
 * glove-stream-motes-carry-the-players-travel-velocity
 *
 * @param rgb         the drip's color
 * @param velocity    the drip's velocity at launch, blocks per tick
 * @param pull        the change in the drip's velocity each tick
 * @param flightTicks the ticks the drip flies before it splats, {@link #UNTIL_IT_STRIKES} for a drip flying its arc
 */
public record SprayDrip(int rgb, Vec3 velocity, Vec3 pull, int flightTicks) {

    /** The flight ticks of a drip flying its arc until it strikes, with no planned end. */
    static final int UNTIL_IT_STRIKES = 0;
    /** The drip's pace along the line to its end, blocks per tick. */
    private static final double CRUISE_SPEED = 0.6;
    /** The arc's gravity, blocks per tick per tick; a drip flying eight blocks peaks about half a block up. */
    private static final double ARC_GRAVITY = 0.02;
    private static final Vec3 ARC_PULL = new Vec3(0, -ARC_GRAVITY, 0);
    private static final double HALF = 0.5;
    private static final double TWICE = 2.0;

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
     * A drip flying its arc from a launch until it strikes.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param launch the drip's launch velocity, blocks per tick, the caster's travel carried in it
     * @param rgb    the drip's color
     * @return the drip
     */
    static SprayDrip thrown(Vec3 launch, int rgb) {
        return new SprayDrip(rgb, launch, ARC_PULL, UNTIL_IT_STRIKES);
    }

    /**
     * Plans a drip's flight onto its end: the ticks its pace needs, and a
     * launch lifted for its arc and carrying the caster's travel, which the
     * pull bends back out over the flight. A one-tick flight carries no travel.
     *
     * @param apex   the glove the drip leaves
     * @param end    where the drip splats
     * @param travel the caster's travel velocity, blocks per tick
     * @param rgb    the drip's color
     * @return the drip
     */
    static SprayDrip toward(Vec3 apex, Vec3 end, Vec3 travel, int rgb) {
        Vec3 gap = end.subtract(apex);
        int ticks = Math.max(1, (int) Math.ceil(gap.length() / CRUISE_SPEED));
        Vec3 velocity = gap.scale(1.0 / ticks).subtract(ARC_PULL.scale((ticks - 1) * HALF));
        if (ticks == 1) {
            return new SprayDrip(rgb, velocity, ARC_PULL, ticks);
        }
        return new SprayDrip(rgb, velocity.add(travel), ARC_PULL.subtract(travel.scale(TWICE / (ticks - 1))),
                ticks);
    }

    /**
     * Whether the drip flies its arc until it strikes, rather than a planned flight onto a coat ray's end.
     *
     * @return true for a drip thrown on its arc
     */
    boolean arcs() {
        return flightTicks == UNTIL_IT_STRIKES;
    }

    /**
     * One tick of a planned flight: the drip moves by its velocity, then the
     * pull bends the velocity.
     *
     * @param position where the drip stands
     * @param velocity the drip's velocity, blocks per tick
     * @param flown    the ticks the drip has flown
     * @return where the drip stands after the tick, and whether it splats there
     */
    Step step(Vec3 position, Vec3 velocity, int flown) {
        return new Step(position.add(velocity), velocity.add(pull), flown + 1 >= flightTicks, false);
    }

    /**
     * One tick of the drip's arc, the server drip's own: it splats on the
     * point its segment meets a block, and ends where it leaves the airspace.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param position where the drip stands
     * @param velocity the drip's velocity, blocks per tick
     * @param clip     the blocks its segment may meet
     * @param airspace where it may fly
     * @return where the drip stands after the tick, and whether it splats there or is lost
     */
    Step step(Vec3 position, Vec3 velocity, CoatRay.BlockClip clip, DripArc.Airspace airspace) {
        DripArc.Step arc = DripArc.step(position, velocity, clip, airspace);
        return new Step(arc.position(), arc.velocity(), arc.strike().isPresent(), arc.lost());
    }

    /**
     * Where a tick of flight leaves a drip.
     *
     * @param position where the drip stands
     * @param velocity the drip's velocity, blocks per tick
     * @param splats   whether the drip has reached its end, splatting there and leaving the world
     * @param lost     whether the drip left the airspace unstruck, leaving the world with no splat
     */
    record Step(Vec3 position, Vec3 velocity, boolean splats, boolean lost) {
    }
}
