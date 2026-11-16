package com.mercuriusxeno.goo.block.ability;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The souls that pop now and then off a mob standing in Leech goo, vanilla's
 * soul particle rising from its body beside the hex wisps it sheds; the goo
 * itself pops none. Whether a mob pops one this tick, and where on its body
 * it rises from.
 * soul-particles-pop-from-leech-goo-now-and-then
 */
public final class LeechSouls {

    private LeechSouls() {
    }

    /**
     * Whether a living thing pops a soul this client tick.
     *
     * @param standsInLeech whether the thing stands in Leech goo
     * @param draw          a random draw in [0, 1)
     * @param rate          the chance a standing thing pops a soul each client tick
     * @return true where it stands in the goo and the draw falls under the rate
     */
    public static boolean pops(boolean standsInLeech, double draw, double rate) {
        return standsInLeech && draw < rate;
    }

    /**
     * Where on a mob's body a soul rises from: a point inside its bounding
     * box, placed across each axis by a draw.
     *
     * @param body    the mob's bounding box
     * @param acrossX where across the box, west to east, a draw in [0, 1)
     * @param upY     where up the box, feet to head, a draw in [0, 1)
     * @param acrossZ where across the box, north to south, a draw in [0, 1)
     * @return the point
     */
    public static Vec3 bodyPoint(AABB body, double acrossX, double upY, double acrossZ) {
        return new Vec3(across(body.minX, body.maxX, acrossX), across(body.minY, body.maxY, upY),
                across(body.minZ, body.maxZ, acrossZ));
    }

    /**
     * A draw's place between two bounds, kept short of the upper, since a
     * draw just under one can round onto it and the box holds its lower
     * faces alone.
     *
     * @param lower the lower bound
     * @param upper the upper bound
     * @param draw  a draw in [0, 1)
     * @return the place, inside [lower, upper)
     */
    private static double across(double lower, double upper, double draw) {
        return Math.min(lower + draw * (upper - lower), Math.nextDown(upper));
    }
}
