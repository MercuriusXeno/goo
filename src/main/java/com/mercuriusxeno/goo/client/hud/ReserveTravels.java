package com.mercuriusxeno.goo.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * The half hearts travelling from the health bar to the reserve row behind
 * it while Reserve drains (decision reserve-hearts-sit-behind-the-bar). The
 * client reads a travel from the health halves the bar loses while a reserve
 * stands and the player is not flinching, so no packet carries it: a hit
 * flinches the player, and the silent drain never does.
 */
final class ReserveTravels {

    /** Ticks a half heart takes to reach the reserve row. */
    static final float TRAVEL_TICKS = 12f;
    /** Pixels a travelling half rises above the straight line at mid flight. */
    static final float ARC_PIXELS = 6f;
    private static final int HALVES_PER_SLOT = 2;
    private static final int NO_READING = -1;

    private int lastHealthHalves = NO_READING;
    private final List<Travel> travelling = new ArrayList<>();

    /**
     * A point on the HUD, in GUI pixels.
     *
     * @param x the left edge
     * @param y the top edge
     */
    record Point(float x, float y) {
    }

    /**
     * One half heart travelling to the reserve row.
     *
     * @param fromSlot the health slot it left
     * @param fromHalf the half of that slot it left: zero for the left, one for the right
     * @param toSlot   the reserve slot it lands in
     * @param start    the GUI time it set off
     */
    record Travel(int fromSlot, int fromHalf, int toSlot, float start) {

        /**
         * How far along its flight the half is.
         *
         * @param now the GUI time
         * @return zero as it leaves the bar, one as it lands
         */
        float progress(float now) {
            return Math.clamp((now - start) / TRAVEL_TICKS, 0f, 1f);
        }
    }

    /**
     * The point a travelling half stands at: on the line from its health slot
     * to its reserve slot, raised by an arc that peaks at mid flight.
     *
     * @param from     where the half left the bar
     * @param to       where it lands behind the bar
     * @param progress how far along its flight it is, zero to one
     * @return the point to draw the half at
     */
    static Point along(Point from, Point to, float progress) {
        float lift = ARC_PIXELS * (float) Math.sin(Math.PI * progress);
        return new Point(from.x() + (to.x() - from.x()) * progress,
                from.y() + (to.y() - from.y()) * progress - lift);
    }

    /**
     * The travels a drop in health starts, rightmost half first, each landing
     * in the reserve slot the next banked half fills.
     *
     * @param before        the health halves at the last frame
     * @param after         the health halves now
     * @param draining      whether a reserve stands and the player is not flinching
     * @param reserveHalves the reserve halves standing now
     * @param now           the GUI time
     * @return a travel per half lost; empty when the drop is no drain
     */
    static List<Travel> travelsFor(int before, int after, boolean draining, int reserveHalves, float now) {
        List<Travel> travels = new ArrayList<>();
        if (!draining) {
            return travels;
        }
        int toSlot = reserveHalves / HALVES_PER_SLOT;
        for (int half = before - 1; half >= after; half--) {
            travels.add(new Travel(half / HALVES_PER_SLOT, half % HALVES_PER_SLOT, toSlot, now));
        }
        return travels;
    }

    /**
     * Reads the bar as the HUD sees it this frame, starting a travel on every
     * health half lost to the drain since the last frame, and dropping
     * travels that have landed.
     *
     * @param healthHalves  the player's health in half hearts, rounded up as vanilla draws it
     * @param draining      whether a reserve stands and the player is not flinching
     * @param reserveHalves the reserve halves standing
     * @param now           the GUI time
     * @return the travels playing
     */
    List<Travel> update(int healthHalves, boolean draining, int reserveHalves, float now) {
        if (lastHealthHalves != NO_READING) {
            travelling.addAll(travelsFor(lastHealthHalves, healthHalves, draining, reserveHalves, now));
        }
        lastHealthHalves = healthHalves;
        travelling.removeIf(travel -> travel.progress(now) >= 1f);
        return List.copyOf(travelling);
    }
}
