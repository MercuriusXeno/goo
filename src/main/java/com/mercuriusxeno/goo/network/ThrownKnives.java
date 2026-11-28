package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.OwnMinions;
import com.mercuriusxeno.goo.ability.TravelForm;
import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.DripArc;
import com.mercuriusxeno.goo.throwing.FenceRails;
import com.mercuriusxeno.goo.throwing.ProjectileFlight;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Knives' thrown knives in flight on the server, each flown a tick at a time
 * as a projectile: it keeps the path it was thrown on whatever the mobs do,
 * dropping like an arrow; a knife meeting a block stops there, passing
 * between a fence's rails, a knife meeting a mob runs the ability's program
 * on it, and a knife whose flight runs out or leaves the world is spent. Dart's
 * javelins fly the same flight, and both pass through the thrower's own
 * led and charmed mobs.
 * decision dart-does-not-seek-or-highlight
 * decision knives-and-dart-function-as-projectiles
 * decision knives-do-not-seek-you-just-throw-them
 * decision knives-and-javelins-drop-like-an-arrow
 */
public final class ThrownKnives {

    private final List<Flight> flying = new ArrayList<>();

    /**
     * One knife in flight.
     *
     * @param thrower   the throwing player
     * @param gooType   the ability's goo type
     * @param abilityId the ability the knife lands
     * @param form      the form it flies in, the blob for a glass knife
     * @param position  where it stands
     * @param velocity  its velocity, blocks per tick
     * @param ticks     the ticks it has flown
     */
    public record Flight(ServerPlayer thrower, ResourceKey<GooTypeDefinition> gooType, String abilityId,
                         TravelForm form, Vec3 position, Vec3 velocity, int ticks) {
    }

    /**
     * Throws a knife from where it hovers, and tells the players watching.
     *
     * @param thrower   the throwing player
     * @param gooType   the ability's goo type
     * @param abilityId the ability the knife lands
     * @param start     where it leaves
     * @param velocity  its launch velocity, blocks per tick
     * @param form      the form it flies in, a javelin's Dart or Spear and a glass knife's blob, which watching
     *                  clients draw
     * @param sound     the sound the throw makes as it leaves, which watching clients play
     */
    public void launch(ServerPlayer thrower, ResourceKey<GooTypeDefinition> gooType, String abilityId, Vec3 start,
                       Vec3 velocity, TravelForm form, Optional<SoundCue> sound) {
        flying.add(new Flight(thrower, gooType, abilityId, form, start, velocity, 0));
        EntityVisuals.sendToWatchers(thrower, new GlassKnifePayload(thrower.getId(), start, velocity,
                form.morphs() ? GooTypes.id(gooType) : GlassKnifePayload.GLASS, form, sound));
    }

    /**
     * @param thrower the throwing player
     * @return the thrower's knives in flight, in the order thrown
     */
    public List<Flight> flyingFrom(ServerPlayer thrower) {
        return flying.stream().filter(flight -> flight.thrower() == thrower).toList();
    }

    /**
     * Flies every knife one tick, striking with each that meets a mob and
     * dropping each whose flight ends.
     *
     * @param now the server tick
     */
    public void tick(int now) {
        List<Flight> flown = new ArrayList<>(flying.size());
        for (Flight flight : flying) {
            if (!flight.thrower().isRemoved()) {
                fly(flight, now).ifPresent(flown::add);
            }
        }
        flying.clear();
        flying.addAll(flown);
    }

    // The knife a tick later, empty once its flight ends.
    private static Optional<Flight> fly(Flight flight, int now) {
        ServerLevel level = flight.thrower().level();
        DripArc.Step step = ProjectileFlight.step(flight.position(), flight.velocity(), ProjectileFlight.ARROW,
                FenceRails.clipping(level), livingNear(level, flight.thrower()),
                DripFlights.airspaceOf(level));
        step.living().ifPresent(struck -> strike(flight, struck, now));
        if (step.ended() || flight.ticks() + 1 >= ThrownKnife.MOST_FLIGHT_TICKS) {
            return Optional.empty();
        }
        return Optional.of(new Flight(flight.thrower(), flight.gooType(), flight.abilityId(), flight.form(),
                step.position(), step.velocity(), flight.ticks() + 1));
    }

    // The living a flight may meet: any but the thrower and the thrower's own led or charmed mobs.
    private static DripArc.LivingNear livingNear(ServerLevel level, ServerPlayer thrower) {
        DripArc.LivingNear near = DripFlights.livingNear(level, thrower.getId());
        return (from, to) -> near.along(from, to).stream().filter(box -> !(level.getEntity(box.entityId())
                instanceof LivingEntity living && OwnMinions.ownMinionOf(thrower, living))).toList();
    }

    /**
     * Lands the ability on the mob a knife met, aimed along its flight.
     *
     * @param flight the knife
     * @param struck the mob's box
     * @param now    the server tick
     */
    private static void strike(Flight flight, DripArc.LivingBox struck, int now) {
        ServerLevel level = flight.thrower().level();
        if (!(level.getEntity(struck.entityId()) instanceof LivingEntity mob) || !mob.isAlive()) {
            return;
        }
        GooServerState.of(level.getServer()).gooEffects().enqueue(new GooEffectScheduler.PendingEffect(now,
                level, flight.thrower(), flight.gooType(), mob.getId(), mob.blockPosition(), null,
                flight.abilityId(), new GooEffectScheduler.Aim(flight.position(), flight.velocity().normalize())));
    }

    /**
     * @return whether a knife is in flight
     */
    public boolean hasPending() {
        return !flying.isEmpty();
    }

    /** Drops every knife in flight, as a server stop does. */
    public void clear() {
        flying.clear();
    }
}
