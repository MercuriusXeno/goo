package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.throwing.DripArc;
import com.mercuriusxeno.goo.throwing.FenceRails;
import com.mercuriusxeno.goo.throwing.ProjectileFlight;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The bone spikes Spurs cells shoot, in flight on the server, each flown a
 * tick at a time as Dart's javelins fly: it keeps the path it was shot on,
 * dropping like an arrow; a spike meeting a block stops there, a spike
 * meeting a mob runs the spurs step's strike on it, and a spike whose flight
 * runs out or leaves the world is spent. A spike passes through the player
 * who placed the cell it left, and watching clients draw it as a javelin in
 * Osseous's goo.
 * spurs-shoots-bone-spikes-at-walkers
 */
public final class BoneSpikes {

    /** The entity id no entity holds, a spike's thrower where no player placed its cell. */
    private static final int NO_ENTITY = -1;

    private final List<Flight> flying = new ArrayList<>();

    /**
     * One bone spike in flight.
     *
     * @param level    the level it flies in
     * @param placer   the player who placed the cell it left, null for nobody
     * @param strike   the steps run on the mob it meets
     * @param position where it stands
     * @param velocity its velocity, blocks per tick
     * @param ticks    the ticks it has flown
     */
    public record Flight(ServerLevel level, @Nullable ServerPlayer placer, List<Step> strike, Vec3 position,
                         Vec3 velocity, int ticks) {
    }

    /**
     * Shoots a bone spike from a cell, and tells the players watching.
     *
     * @param level    the level
     * @param placer   the player who placed the cell, null for nobody
     * @param strike   the steps run on the mob it meets
     * @param start    where it leaves
     * @param velocity its launch velocity, blocks per tick
     */
    public void launch(ServerLevel level, @Nullable ServerPlayer placer, List<Step> strike, Vec3 start,
                       Vec3 velocity) {
        flying.add(new Flight(level, placer, strike, start, velocity, 0));
        EntityVisuals.sendToWatchersOf(level, start, new GlassKnifePayload(placerId(placer), start, velocity,
                GooTypes.id(GooTypes.OSSEOUS), Optional.empty()));
    }

    /**
     * Flies every bone spike one tick, striking with each that meets a mob
     * and dropping each whose flight ends.
     */
    public void tick() {
        List<Flight> flown = new ArrayList<>(flying.size());
        for (Flight flight : flying) {
            fly(flight).ifPresent(flown::add);
        }
        flying.clear();
        flying.addAll(flown);
    }

    // The spike a tick later, empty once its flight ends.
    private static Optional<Flight> fly(Flight flight) {
        ServerLevel level = flight.level();
        DripArc.Step step = ProjectileFlight.step(flight.position(), flight.velocity(), ProjectileFlight.ARROW,
                FenceRails.clipping(level), DripFlights.livingNear(level, placerId(flight.placer())),
                DripFlights.airspaceOf(level));
        step.living().ifPresent(struck -> strike(flight, struck));
        if (step.ended() || flight.ticks() + 1 >= ThrownKnife.MOST_FLIGHT_TICKS) {
            return Optional.empty();
        }
        return Optional.of(new Flight(level, flight.placer(), flight.strike(), step.position(), step.velocity(),
                flight.ticks() + 1));
    }

    // Runs the strike on the mob a spike met, crediting the cell's placer.
    private static void strike(Flight flight, DripArc.LivingBox struck) {
        if (flight.level().getEntity(struck.entityId()) instanceof LivingEntity mob && mob.isAlive()) {
            new ProgramBehavior(flight.strike()).tick(EntityHost.struckByField(flight.level(), mob, flight.placer()));
        }
    }

    private static int placerId(@Nullable ServerPlayer placer) {
        return placer != null ? placer.getId() : NO_ENTITY;
    }

    /**
     * Whether a bone spike flies inside a box of a level.
     *
     * @param level the level
     * @param box   the box
     * @return true where a spike in flight stands in the box
     */
    public boolean fliesWithin(ServerLevel level, AABB box) {
        return flying.stream().anyMatch(flight -> flight.level() == level && box.contains(flight.position()));
    }

    /** Drops every bone spike in flight, as a server stop does. */
    public void clear() {
        flying.clear();
    }
}
