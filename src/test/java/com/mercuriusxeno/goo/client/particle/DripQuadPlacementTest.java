package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.DripFall;
import com.mercuriusxeno.goo.block.tap.TapStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers where a drip's falling quad and splat quad sit against the surface the drip lands on. */
class DripQuadPlacementTest {

    /** The block top the drip lands on. */
    private static final double SURFACE_Y = 64.0;

    /** Spacing of the spigot heights the fall sweeps, in blocks. */
    private static final double FALL_STEP = 0.01;

    /** Spigot heights swept, one per step, up to three blocks above the lowest. */
    private static final int FALL_DISTANCES = 300;

    /** The lowest a spigot sits over a surface: a tap standing on a full block. */
    private static final double LOWEST_SPIGOT_Y = SURFACE_Y + TapStream.SPIGOT_UNDERSIDE_LOCAL_Y;

    /** The tap-drip's leave speed, downward. */
    private static final double LEAVE_SPEED = -0.05;

    /**
     * The drip's y each tick from spawn to contact, in the particle's order:
     * gravity, move clamped at the surface, drag.
     *
     * @param spigotY the y the drip spawns at
     * @return every y the drip's collision box bottom takes, spawn first, landing last
     */
    private static List<Double> fallPath(double spigotY) {
        List<Double> path = new ArrayList<>();
        double y = spigotY;
        double speed = LEAVE_SPEED;
        path.add(y);
        while (y > SURFACE_Y) {
            speed -= DripFall.GRAVITY;
            y = Math.max(SURFACE_Y, y + speed);
            speed *= DripFall.DRAG;
            path.add(y);
        }
        return path;
    }

    @Nested
    class FallingQuad {

        @ParameterizedTest
        @ValueSource(floats = {0.1f, 0.2f})
        void everyTickOfTheFallSitsAboveTheSurfaceByTheMargin(float halfSize) {
            for (int drop = 0; drop <= FALL_DISTANCES; drop++) {
                assertRenderedTicksClear(fallPath(LOWEST_SPIGOT_Y + drop * FALL_STEP), halfSize);
            }
        }

        /**
         * The drop draws every tick it lives; the contact tick removes it and draws the splat.
         */
        private void assertRenderedTicksClear(List<Double> path, float halfSize) {
            for (int tick = 0; tick < path.size() - 1; tick++) {
                double lowest = DripQuadPlacement.fallQuadLowestY(path.get(tick), halfSize);
                assertTrue(lowest >= SURFACE_Y + DripQuadPlacement.SURFACE_MARGIN,
                        "tick " + tick + " of " + (path.size() - 1) + ": drip y " + path.get(tick)
                                + ", quad lowest y " + lowest + ", surface y " + SURFACE_Y);
            }
        }
    }

    @Nested
    class Splat {

        @ParameterizedTest
        @ValueSource(doubles = {SURFACE_Y, 70.5})
        void splatLiesAboveTheSurfaceByTheMargin(double landingY) {
            double splatY = DripQuadPlacement.landQuadY(landingY);
            assertTrue(splatY >= landingY + DripQuadPlacement.SURFACE_MARGIN && splatY > landingY,
                    "splat y " + splatY + ", surface y " + landingY);
        }
    }
}
