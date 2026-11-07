package com.mercuriusxeno.goo.ability.world;

import net.minecraft.world.phys.Vec3;

/**
 * Where Meteo's meteor appears and where it stands as it falls: high above
 * its target, then straight down to it over its fall time. Held apart from
 * the entity so the path reads without a mod loader.
 * decision meteo-needs-a-clear-sky
 */
public final class MeteorFall {

    /**
     * Blocks above the target a meteor appears at: high, high up, over the
     * world's top where need be, so it is seen coming from far off rather
     * than popping in just over the target (operator ruling 2026-11-06).
     */
    public static final int FALL_HEIGHT = 200;

    private MeteorFall() {
    }

    /**
     * Where a meteor appears over its target.
     *
     * @param target the point it strikes
     * @return the point the fall height above the target
     */
    public static Vec3 startAbove(Vec3 target) {
        return target.add(0, FALL_HEIGHT, 0);
    }

    /**
     * Where a meteor stands a share of the way through its fall: it falls as
     * a rock falls, from rest at its start and gathering speed, so it leaves
     * the sky at once and slowly, the beat that lets it be seen coming, and
     * strikes fast (operator ruling 2026-11-06: no hang before it falls).
     * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
     *
     * @param start  where it appeared
     * @param target where it strikes
     * @param age    the ticks it has fallen, a fraction between ticks for a frame
     * @param ticks  the ticks its fall lasts
     * @return the point on the straight line from start to target
     */
    public static Vec3 positionAt(Vec3 start, Vec3 target, double age, int ticks) {
        double travelled = Math.min(1.0, age / ticks);
        return start.lerp(target, travelled * travelled);
    }
}
