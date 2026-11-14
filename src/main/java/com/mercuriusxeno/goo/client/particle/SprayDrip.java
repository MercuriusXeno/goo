package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * One Goop or Slick spray drip's flight: it flies the arc the server's drip
 * flies, from its launch under the same gravity and drag, and splats where its own
 * segment meets a block; only its color is rolled.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 *
 * @param rgb      the drip's color
 * @param velocity the drip's velocity at launch, blocks per tick
 */
public record SprayDrip(int rgb, Vec3 velocity) {

    /**
     * Throws a drip in a palette's colors at a launch.
     *
     * @param launch  the drip's launch velocity, blocks per tick
     * @param palette the colors the drip blends between
     * @param random  the source the drip's color is rolled from
     * @return the drip
     */
    static SprayDrip launch(Vec3 launch, BlendPalette palette, RandomSource random) {
        return new SprayDrip(palette.colorAt(random.nextFloat()), launch);
    }

    /**
     * One tick of the drip's arc, the server drip's own: it splats on the
     * point its segment meets a block or a living thing, and ends where it
     * leaves the airspace.
     * goop-wobbles-a-mob-its-drip-strikes
     *
     * @param position where the drip stands
     * @param velocity the drip's velocity, blocks per tick
     * @param clip     the blocks its segment may meet
     * @param living   the living its segment may meet
     * @param airspace where it may fly
     * @return where the drip stands after the tick, and whether it splats there or is lost
     */
    Step step(Vec3 position, Vec3 velocity, CoatRay.BlockClip clip, DripArc.LivingNear living,
              DripArc.Airspace airspace) {
        DripArc.Step arc = DripArc.step(position, velocity, clip, living, airspace);
        return new Step(arc.position(), arc.velocity(), arc.strike().isPresent() || arc.living().isPresent(),
                arc.lost());
    }

    /**
     * Where a tick of flight leaves a drip.
     *
     * @param position where the drip stands
     * @param velocity the drip's velocity, blocks per tick
     * @param splats   whether the drip struck a block or a living thing, splatting there and leaving the world
     * @param lost     whether the drip left the airspace unstruck, leaving the world with no splat
     */
    record Step(Vec3 position, Vec3 velocity, boolean splats, boolean lost) {
    }
}
