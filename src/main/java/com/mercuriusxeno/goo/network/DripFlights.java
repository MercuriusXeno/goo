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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
 * A drip that meets a living thing before a block ends there, striking it,
 * where its ability strikes the living.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * goop-wobbles-a-mob-its-drip-strikes
 */
public final class DripFlights {

    private final List<Flight> flights = new ArrayList<>();

    /**
     * Who threw a drip and what its strike does, shared by every drip of one throw.
     *
     * @param dimension     the level the drip flies in
     * @param caster        the player who threw it, who pays for what it lays and is the source of what it strikes
     * @param casterId      the caster's entity id, whom the drip passes through
     * @param ability       the ability that threw it
     * @param landing       what its strike on a block lays, empty where it lays nothing
     * @param strikesLiving whether it ends on a living thing it meets, running the ability's entity steps on it
     */
    record Thrower(ResourceKey<Level> dimension, UUID caster, int casterId, AbilityDefinition ability,
                   Optional<DripLanding> landing, boolean strikesLiving) {
    }

    /**
     * One drip in flight.
     *
     * @param thrower  who threw it and what its strike does
     * @param position where it stands
     * @param velocity its velocity, blocks per tick
     */
    record Flight(Thrower thrower, Vec3 position, Vec3 velocity) {

        /**
         * @param step a tick of the drip's flight
         * @return the drip where the tick left it
         */
        Flight after(DripArc.Step step) {
            return new Flight(thrower, step.position(), step.velocity());
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
            Thrower thrower = flight.thrower();
            ServerLevel level = server.getLevel(thrower.dimension());
            if (level == null) {
                continue;
            }
            DripArc.Step step = DripArc.step(flight.position(), flight.velocity(), CoatRay.clipping(level),
                    thrower.strikesLiving() ? livingNear(level, thrower.casterId()) : DripArc.NO_LIVING,
                    airspaceOf(level));
            if (!step.ended()) {
                flying.add(flight.after(step));
            }
            step.strike().ifPresent(strike -> land(server, level, thrower, strike, fed));
            step.living().ifPresent(struck -> strike(server, level, thrower, struck));
        }
        flights.clear();
        flights.addAll(flying);
    }

    /**
     * Lands what a struck drip lays, its caster paying; a caster no longer
     * on the server pays for nothing, so their drip lays nothing.
     *
     * @param server  the server
     * @param level   the drip's level
     * @param thrower who threw the drip
     * @param strike  where it struck
     * @param fed     the cells each caster's drips fed this tick, which this landing joins
     */
    private static void land(MinecraftServer server, ServerLevel level, Thrower thrower, CoatRay.BlockStrike strike,
                             Set<Fed> fed) {
        ServerPlayer caster = server.getPlayerList().getPlayer(thrower.caster());
        if (caster == null || thrower.landing().isEmpty()) {
            return;
        }
        DripLanding landing = thrower.landing().get();
        ChannelHost host = PlayerHost.landing(level, caster, GooStreamHandler.placementPrice(caster,
                thrower.ability()));
        landing.cellFed(host, strike)
                .filter(cell -> fed.add(new Fed(thrower.caster(), thrower.dimension(), cell.immutable())))
                .ifPresent(cell -> landing.feed(host, cell));
    }

    /**
     * Runs the ability's entity steps on the living thing a drip struck, its
     * caster as their source; a caster no longer on the server strikes nothing.
     * goop-wobbles-a-mob-its-drip-strikes
     *
     * @param server  the server
     * @param level   the drip's level
     * @param thrower who threw the drip
     * @param struck  the living thing's box
     */
    private static void strike(MinecraftServer server, ServerLevel level, Thrower thrower, DripArc.LivingBox struck) {
        ServerPlayer caster = server.getPlayerList().getPlayer(thrower.caster());
        if (caster != null && level.getEntity(struck.entityId()) instanceof LivingEntity living && living.isAlive()) {
            GooStreamHandler.strikeLiving(level, caster, living, thrower.ability());
        }
    }

    /**
     * The living things a level holds near a drip's segment, the caster aside.
     * goop-wobbles-a-mob-its-drip-strikes
     *
     * @param level    the level
     * @param casterId the caster's entity id, whom a drip passes through
     * @return the living near each segment, as boxes
     */
    public static DripArc.LivingNear livingNear(Level level, int casterId) {
        return (from, to) -> level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to),
                        living -> living.getId() != casterId && living.isAlive() && !living.isSpectator())
                .stream().map(living -> new DripArc.LivingBox(living.getId(), living.getBoundingBox())).toList();
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
