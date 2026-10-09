package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.FlightHost;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.TravelingStep;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The thrown blobs whose ability does something while it flies: each server
 * tick of a flight, the traveling step's children run on the point the blob
 * has reached along the same arc the clients draw, until it lands
 * (decision orb-carries-a-swirling-nova).
 */
public final class TravelingFlights {

    private static final String LOG_REFUSED = "Traveling steps of {} refused to load on a blob in flight: {}";

    /**
     * One blob flying with steps to run along the way.
     *
     * @param level       the level it flies in
     * @param abilityId   the ability thrown, for a refusal's log
     * @param start       where it left the hand
     * @param end         where it lands
     * @param peak        the arc's peak height
     * @param startTick   the server tick it left the hand
     * @param travelTicks the ticks it flies
     * @param traveling   what it does while it flies
     */
    record Flight(ServerLevel level, String abilityId, Vec3 start, Vec3 end, double peak, int startTick,
                  int travelTicks, TravelingStep traveling) {

        /**
         * Where the blob is at a server tick: on its arc, at the share of
         * the flight the tick has reached.
         *
         * @param tick the server tick
         * @return the blob's position
         */
        Vec3 at(int tick) {
            double share = Math.clamp((double) (tick - startTick) / travelTicks, 0.0, 1.0);
            return ThrowArc.arcPoint(start, end, share, peak);
        }

        /**
         * Whether the blob has landed by a server tick.
         *
         * @param tick the server tick
         * @return true from its landing tick on
         */
        boolean landedBy(int tick) {
            return tick - startTick >= travelTicks;
        }
    }

    private final List<Flight> live = new ArrayList<>();

    /**
     * Starts running a flight's traveling steps each tick until it lands.
     *
     * @param flight the flight
     */
    void track(Flight flight) {
        live.add(flight);
    }

    /**
     * Runs each live flight's traveling steps on where it is this tick, and
     * drops every flight that has landed.
     *
     * @param tick the server tick
     */
    void tick(int tick) {
        live.removeIf(flight -> flight.landedBy(tick));
        for (Flight flight : List.copyOf(live)) {
            try {
                ProgramBehavior.forHost(flight.traveling().steps(), HostKind.FLIGHT)
                        .tick(new FlightHost(flight.level(), flight.at(tick)));
            } catch (ProgramLoadException e) {
                Goo.LOGGER.error(LOG_REFUSED, flight.abilityId(), e.getMessage());
                live.remove(flight);
            }
        }
    }

    /** Drops every flight, as a server stop does. */
    void clear() {
        live.clear();
    }
}
