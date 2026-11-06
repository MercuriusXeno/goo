package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a sunbeam ray's drawn end sits each frame. The server's ray arrives
 * once a tick, a round trip behind the look, so the local caster's own ray
 * is cast each frame from its eye along its aim that frame; another
 * caster's ray ends where the server struck.
 * decision diagnose-and-correct-the-sunbeam-jitter
 */
final class SunbeamRayEnd {

    /** Below this squared gap the aim point sits on the eye and gives no direction. */
    private static final double SHORTEST_AIM_SQUARED = 1e-6;

    private SunbeamRayEnd() {
    }

    /**
     * A sight line cast against the world.
     */
    @FunctionalInterface
    interface SightLine {

        /**
         * Where a sight line from a point toward its reach stops.
         *
         * @param from  where the line starts
         * @param reach the furthest point it reaches
         * @return the point it stops at, the reach where nothing stops it
         */
        Vec3 stop(Vec3 from, Vec3 reach);
    }

    /**
     * The local caster's aim this frame.
     *
     * @param eye      the caster's eye this frame
     * @param aimPoint the point the hold aims at this frame
     */
    record OwnAim(Vec3 eye, Vec3 aimPoint) {
    }

    /**
     * The ray's drawn end this frame.
     *
     * @param serverEnd where the server's latest ray struck
     * @param own       the local caster's aim this frame, null for another caster's ray
     * @param range     how far the ray reaches, in blocks
     * @param sight     the sight line the world casts
     * @return the drawn end
     */
    static Vec3 drawnEnd(Vec3 serverEnd, @Nullable OwnAim own, double range, SightLine sight) {
        if (own == null) {
            return serverEnd;
        }
        Vec3 aim = own.aimPoint().subtract(own.eye());
        if (aim.lengthSqr() < SHORTEST_AIM_SQUARED) {
            return serverEnd;
        }
        return sight.stop(own.eye(), own.eye().add(aim.normalize().scale(range)));
    }
}
