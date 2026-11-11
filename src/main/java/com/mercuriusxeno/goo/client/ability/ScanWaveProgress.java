package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;

/**
 * Where a scan's drawn wave stands on its way to the landing: at the glove
 * as it leaves, at the landing on the tick the server sounds there, and
 * along the straight line between at an even pace
 * (decision click-is-a-hit-scan-drawn-as-a-delayed-wave).
 */
public final class ScanWaveProgress {

    private ScanWaveProgress() {
    }

    /**
     * The wave's point a number of ticks after it left.
     *
     * @param from  the glove hand the wave left
     * @param to    the landing it reaches
     * @param delay the ticks it takes to arrive
     * @param tick  the ticks since it left, partial ticks included
     * @return the wave's point, held at the landing once it has arrived
     */
    public static Vec3 at(Vec3 from, Vec3 to, int delay, float tick) {
        double share = delay <= 0 ? 1 : Math.clamp(tick / delay, 0f, 1f);
        return from.add(to.subtract(from).scale(share));
    }
}
