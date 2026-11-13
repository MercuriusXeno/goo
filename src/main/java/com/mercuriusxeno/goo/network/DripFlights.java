package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.ChannelHost;
import com.mercuriusxeno.goo.ability.program.DripLanding;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The spray drips in flight on one server. Each drip a stream throws flies
 * its arc here, stepped every server tick, and lands what its ability lays
 * where it strikes; the flights outlive the hold that threw them, so a drip
 * still in flight after release lands its coat when it strikes and its
 * caster pays for it then. A drip is plain state, never an entity, and one
 * leaving the build height or the loaded chunks ends unstruck. Each cell
 * takes one strike's share from one caster a tick, however many of their
 * drips strike it, so a cell drips strike every tick builds at one pace.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 */
public final class DripFlights {

    private final List<Flight> flights = new ArrayList<>();

    /**
     * One drip in flight.
     *
     * @param dimension the level the drip flies in
     * @param caster    the player who threw it, who pays for what it lays
     * @param ability   the ability that threw it
     * @param landing   what its strike lays
     * @param position  where it stands
     * @param velocity  its velocity, blocks per tick
     */
    record Flight(ResourceKey<Level> dimension, UUID caster, AbilityDefinition ability, DripLanding landing,
                  Vec3 position, Vec3 velocity) {

        /**
         * @param step a tick of the drip's flight
         * @return the drip where the tick left it
         */
        Flight after(DripArc.Step step) {
            return new Flight(dimension, caster, ability, landing, step.position(), step.velocity());
        }
    }

    /**
     * A cell one caster's drips fed this tick.
     *
     * @param caster    the caster
     * @param dimension the cell's level
     * @param cell      the cell
     */
    private record Fed(UUID caster, ResourceKey<Level> dimension, BlockPos cell) {
    }

    /**
     * Throws a drip into flight.
     *
     * @param flight the drip at its launch
     */
    void launch(Flight flight) {
        flights.add(flight);
    }

    /**
     * @return true while any drip flies
     */
    public boolean hasPending() {
        return !flights.isEmpty();
    }

    /**
     * Steps every drip one tick, landing each that strikes and dropping each
     * that strikes or leaves the airspace.
     *
     * @param server the ticking server
     */
    public void tick(MinecraftServer server) {
        Set<Fed> fed = new HashSet<>();
        List<Flight> flying = new ArrayList<>(flights.size());
        for (Flight flight : List.copyOf(flights)) {
            ServerLevel level = server.getLevel(flight.dimension());
            if (level == null) {
                continue;
            }
            DripArc.Step step = DripArc.step(flight.position(), flight.velocity(), CoatRay.clipping(level),
                    airspaceOf(level));
            if (!step.ended()) {
                flying.add(flight.after(step));
            }
            step.strike().ifPresent(strike -> land(server, level, flight, strike, fed));
        }
        flights.clear();
        flights.addAll(flying);
    }

    /**
     * Lands what a struck drip lays, its caster paying; a caster no longer
     * on the server pays for nothing, so their drip lays nothing.
     *
     * @param server the server
     * @param level  the drip's level
     * @param flight the drip
     * @param strike where it struck
     * @param fed    the cells each caster's drips fed this tick, which this landing joins
     */
    private static void land(MinecraftServer server, ServerLevel level, Flight flight, CoatRay.BlockStrike strike,
                             Set<Fed> fed) {
        ServerPlayer caster = server.getPlayerList().getPlayer(flight.caster());
        if (caster == null) {
            return;
        }
        ChannelHost host = PlayerHost.landing(level, caster, GooStreamHandler.placementPrice(caster, flight.ability()));
        flight.landing().cellFed(host, strike)
                .filter(cell -> fed.add(new Fed(flight.caster(), flight.dimension(), cell.immutable())))
                .ifPresent(cell -> flight.landing().feed(host, cell));
    }

    /**
     * Where a level holds a drip: inside its build height, in a chunk it holds loaded.
     *
     * @param level the level
     * @return the level's airspace
     */
    public static DripArc.Airspace airspaceOf(Level level) {
        return point -> !level.isOutsideBuildHeight(Mth.floor(point.y))
                && level.hasChunk(SectionPos.blockToSectionCoord(point.x), SectionPos.blockToSectionCoord(point.z));
    }

    /** Drops every drip in flight, as a server stop does. */
    public void clear() {
        flights.clear();
    }
}
