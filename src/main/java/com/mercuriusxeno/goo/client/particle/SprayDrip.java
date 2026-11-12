package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.network.GooStreamHandler;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * One Goop or Slick spray drip's flight: thrown from the glove, it arcs under
 * a light gravity at a steady pace onto the end its launch aims at, the end
 * of its coat ray, and splats there; only its color is rolled.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * goop-and-slick-spray-follows-the-coat
 *
 * @param rgb         the drip's color
 * @param velocity    the drip's velocity at launch, blocks per tick
 * @param flightTicks the ticks the drip flies before it splats
 */
public record SprayDrip(int rgb, Vec3 velocity, int flightTicks) {

    /** The drip's pace along the line to its end, blocks per tick. */
    private static final double CRUISE_SPEED = 0.6;
    /** The arc's pull, blocks per tick per tick; a drip flying eight blocks peaks about half a block up. */
    private static final double ARC_GRAVITY = 0.02;
    private static final double HALF = 0.5;

    /**
     * Throws a drip from the glove: it flies to the end its launch aims at,
     * in the ticks its pace needs, lifted so its arc comes down on that end.
     *
     * @param apex    the glove the drip leaves
     * @param launch  the launch the stream's handler aimed, carrying the caster's travel
     * @param palette the colors the drip blends between
     * @param random  the source the drip's color is rolled from
     * @return the drip
     */
    static SprayDrip launch(Vec3 apex, Vec3 launch, BlendPalette palette, RandomSource random) {
        Vec3 gap = GooStreamHandler.launchEnd(apex, launch).subtract(apex);
        int ticks = Math.max(1, (int) Math.ceil(gap.length() / CRUISE_SPEED));
        Vec3 velocity = gap.scale(1.0 / ticks).add(0, ARC_GRAVITY * (ticks - 1) * HALF, 0);
        return new SprayDrip(palette.colorAt(random.nextFloat()), velocity, ticks);
    }

    /**
     * One tick of the drip's flight: it moves by its velocity, then gravity
     * pulls the velocity down.
     *
     * @param position where the drip stands
     * @param velocity the drip's velocity, blocks per tick
     * @param flown    the ticks the drip has flown
     * @return where the drip stands after the tick, and whether it splats there
     */
    Step step(Vec3 position, Vec3 velocity, int flown) {
        return new Step(position.add(velocity), velocity.subtract(0, ARC_GRAVITY, 0), flown + 1 >= flightTicks);
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
