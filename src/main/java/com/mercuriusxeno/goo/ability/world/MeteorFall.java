package com.mercuriusxeno.goo.ability.world;

import net.minecraft.world.phys.Vec3;

/**
 * Where Meteo's meteor appears and where it stands as it falls: high above
 * its target, then straight down to it over its fall time. Held apart from
 * the entity so the path reads without a mod loader.
 * decision meteo-needs-a-clear-sky
 */
public final class MeteorFall {

    /** Blocks above the target a meteor appears at, capped at the world's top. */
    public static final int FALL_HEIGHT = 48;

    private MeteorFall() {
    }

    /**
     * Where a meteor appears over its target.
     *
     * @param target the point it strikes
     * @param worldTop the highest y the world holds
     * @return the point the fall height above the target, no higher than the world's top
     */
    public static Vec3 startAbove(Vec3 target, int worldTop) {
        return new Vec3(target.x, Math.min(worldTop, target.y + FALL_HEIGHT), target.z);
    }

    /**
     * Where a meteor stands a share of the way through its fall.
     *
     * @param start  where it appeared
     * @param target where it strikes
     * @param age    the ticks it has fallen
     * @param ticks  the ticks its fall lasts
     * @return the point on the straight line from start to target
     */
    public static Vec3 positionAt(Vec3 start, Vec3 target, int age, int ticks) {
        return start.lerp(target, Math.min(1.0, (double) age / ticks));
    }
}
