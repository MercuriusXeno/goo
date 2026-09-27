package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.DripFall;
import com.mercuriusxeno.goo.block.tap.TapStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers where a tap drip's falling quad and splat quad sit between its spigot and the surface it lands on. */
class DripQuadPlacementTest {

    /** The block top the drip lands on. */
    private static final double SURFACE_Y = 64.0;

    /** Spacing of the spigot heights the fall sweeps, in blocks. */
    private static final double FALL_STEP = 0.01;

    /** Spigot heights swept, one per step, up to three blocks above the lowest. */
    private static final int FALL_DISTANCES = 300;

    /** The lowest a spigot sits over a surface: a tap standing on a full block. */
    private static final double LOWEST_SPIGOT_Y = SURFACE_Y + TapStream.SPIGOT_UNDERSIDE_LOCAL_Y;

    /** Float rounding the quad's top may carry past the spigot underside. */
    private static final double TOP_TOLERANCE = 1e-6;

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

        /**
         * Every spigot height the sweep reaches: a tap's drop either hangs
         * from the spigot and falls, or has no room and splats at once.
         */
        @Test
        void everyTickOfAHangingDropSitsBetweenTheSpigotAndTheSurfaceByTheMargin() {
            float halfSize = TapDripParticle.DROP_HALF_SIZE;
            int hung = 0;
            for (int drop = 0; drop <= FALL_DISTANCES; drop++) {
                double spigotY = LOWEST_SPIGOT_Y + drop * FALL_STEP;
                double room = Math.min(spigotY - SURFACE_Y, DripQuadPlacement.hangingDrop(halfSize));
                if (DripQuadPlacement.dropFits(room, halfSize)) {
                    assertHangSwellsFromTheSpigot(spigotY, room, halfSize);
                    assertRenderedTicksClear(spigotY, fallPath(DripQuadPlacement.hangingSpawnY(spigotY, halfSize)),
                            halfSize);
                    hung++;
                }
            }
            assertTrue(hung > 0, "no spigot height in the sweep hung a drop");
        }

        /**
         * Each hang tick draws the drop with its top at the spigot's underside,
         * no taller than the tick before, clear of the surface by the margin,
         * from nothing at the first tick to the full square at the last.
         */
        private void assertHangSwellsFromTheSpigot(double spigotY, double room, float halfSize) {
            float previous = -1f;
            for (int hung = 0; hung <= DripFall.HANG_TICKS; hung++) {
                float progress = DripQuadPlacement.hangProgress(hung, 0f, DripFall.HANG_TICKS);
                float swollen = DripQuadPlacement.hangingHalfSize(halfSize, progress, room);
                double center = DripQuadPlacement.hangingQuadCenterY(spigotY, swollen);
                String at = "spigot y " + spigotY + ", hang tick " + hung + ": half size " + swollen;
                assertEquals(spigotY, center + swollen, TOP_TOLERANCE, at);
                assertTrue(center - swollen >= SURFACE_Y + DripQuadPlacement.SURFACE_MARGIN, at);
                assertTrue(swollen > previous, at);
                previous = swollen;
            }
            assertEquals(0f, DripQuadPlacement.hangingHalfSize(halfSize,
                    DripQuadPlacement.hangProgress(0, 0f, DripFall.HANG_TICKS), room));
            assertEquals(halfSize, previous);
        }

        @Test
        void aTapOverAFullBlockHasNoRoomToHangTheDrop() {
            double room = Math.min(LOWEST_SPIGOT_Y - SURFACE_Y,
                    DripQuadPlacement.hangingDrop(TapDripParticle.DROP_HALF_SIZE));

            assertFalse(DripQuadPlacement.dropFits(room, TapDripParticle.DROP_HALF_SIZE));
        }

        @Test
        void aTapOneBlockHigherHangsTheDrop() {
            double room = Math.min(LOWEST_SPIGOT_Y + 1.0 - SURFACE_Y,
                    DripQuadPlacement.hangingDrop(TapDripParticle.DROP_HALF_SIZE));

            assertTrue(DripQuadPlacement.dropFits(room, TapDripParticle.DROP_HALF_SIZE));
        }

        /**
         * The drop draws every tick it lives; the contact tick removes it and
         * draws the splat. Its quad's top never rises above the spigot's underside.
         */
        private void assertRenderedTicksClear(double spigotY, List<Double> path, float halfSize) {
            for (int tick = 0; tick < path.size() - 1; tick++) {
                double lowest = DripQuadPlacement.fallQuadLowestY(path.get(tick), halfSize);
                double highest = lowest + 2.0 * halfSize;
                String at = "spigot y " + spigotY + ", tick " + tick + " of " + (path.size() - 1)
                        + ": drip y " + path.get(tick) + ", quad " + lowest + " to " + highest;
                assertTrue(lowest >= SURFACE_Y + DripQuadPlacement.SURFACE_MARGIN, at);
                assertTrue(highest <= spigotY + TOP_TOLERANCE, at);
            }
        }
    }

    @Nested
    class HangEnd {

        private final float halfSize = TapDripParticle.DROP_HALF_SIZE;

        @ParameterizedTest
        @ValueSource(doubles = {0.0, 0.125, 0.144})
        void roomShortOfTheHangingDropSplatsAtTheSurface(double room) {
            assertEquals(DripQuadPlacement.HangEnd.SPLAT, DripQuadPlacement.hangEnd(room, halfSize));
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.145, 0.5, 3.0})
        void roomAtOrPastTheHangingDropFalls(double room) {
            assertEquals(DripQuadPlacement.HangEnd.FALL, DripQuadPlacement.hangEnd(room, halfSize));
        }

        @Test
        void theHangingDropIsTheFullSquareAndTheMargin() {
            assertEquals(2.0 * halfSize + DripQuadPlacement.SURFACE_MARGIN,
                    DripQuadPlacement.hangingDrop(halfSize), TOP_TOLERANCE);
        }

        /**
         * A tap over a full block: the drop stands at the spigot, swelling,
         * clear of the surface for every hang tick, then splats.
         */
        @Test
        void aTapOverAFullBlockHangsTheDropForTheHangThenSplats() {
            double room = Math.min(LOWEST_SPIGOT_Y - SURFACE_Y, DripQuadPlacement.hangingDrop(halfSize));
            float previous = 0f;
            for (int hung = 1; hung <= DripFall.HANG_TICKS; hung++) {
                float swollen = DripQuadPlacement.hangingHalfSize(halfSize,
                        DripQuadPlacement.hangProgress(hung, 0f, DripFall.HANG_TICKS), room);
                double center = DripQuadPlacement.hangingQuadCenterY(LOWEST_SPIGOT_Y, swollen);
                String at = "hang tick " + hung + ": half size " + swollen;
                assertTrue(swollen > 0f && swollen >= previous, at);
                assertEquals(LOWEST_SPIGOT_Y, center + swollen, TOP_TOLERANCE, at);
                assertTrue(center - swollen >= SURFACE_Y + DripQuadPlacement.SURFACE_MARGIN - TOP_TOLERANCE, at);
                previous = swollen;
            }
            assertEquals(DripQuadPlacement.HangEnd.SPLAT, DripQuadPlacement.hangEnd(room, halfSize));
        }

        @Test
        void hangProgressInterpolatesWithinATickAndHoldsAtFull() {
            assertEquals(0.625f, DripQuadPlacement.hangProgress(2, 0.5f, DripFall.HANG_TICKS));
            assertEquals(1f, DripQuadPlacement.hangProgress(DripFall.HANG_TICKS, 0.5f, DripFall.HANG_TICKS));
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
