package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Each caster's live held-sweep pulses: a pulse starts where the caster
 * stands on a tick the pace starts one, travels on beside the pulses
 * already out, and leaves once its front has reached its range, so a
 * re-press sends a new front without waiting out the last.
 * decision held-sweeps-share-one-fast-shell-visual
 */
public final class HeldSweepPulses {

    /** The server's pulses, every caster's. */
    public static final HeldSweepPulses SERVER = new HeldSweepPulses();

    private final Map<UUID, List<Pulse>> live = new HashMap<>();
    /** The tick of its pulse each caster's fronts land on, as its sweep's pace last set it. */
    private final Map<UUID, Integer> landsAfter = new HashMap<>();
    private int lastId;

    /**
     * One tick of a caster's hold: drops the pulses whose fronts have
     * landed, starts a pulse where the pace starts one, and answers the
     * pulses still out, oldest first.
     *
     * @param caster   the caster
     * @param position where the caster stands
     * @param held     the hold's age, 1 on the press
     * @param gameTime the game time
     * @param pace     the sweep's pace
     * @return the live pulses, oldest first
     */
    public List<Pulse> advance(UUID caster, Vec3 position, int held, long gameTime, HeldSweepPace pace) {
        List<Pulse> pulses = live.computeIfAbsent(caster, key -> new ArrayList<>());
        landsAfter.put(caster, pace.ticksToRange());
        pulses.removeIf(pulse -> pulse.ageAt(gameTime) > pace.ticksToRange());
        if (pace.startsPulse(held)) {
            lastId++;
            pulses.add(new Pulse(lastId, position, gameTime));
        }
        if (pulses.isEmpty()) {
            live.remove(caster);
            landsAfter.remove(caster);
        }
        return List.copyOf(pulses);
    }

    /**
     * A press of an instant sweep: drops the caster's pulses whose fronts
     * have landed and starts one where the caster stands, beside any still out.
     * decision scry-is-instant-and-no-longer-channeled
     *
     * @param caster   the caster
     * @param position where the caster stands
     * @param gameTime the game time
     * @param pace     the sweep's pace
     * @return the pulse started
     */
    public Pulse start(UUID caster, Vec3 position, long gameTime, HeldSweepPace pace) {
        List<Pulse> pulses = live.computeIfAbsent(caster, key -> new ArrayList<>());
        landsAfter.put(caster, pace.ticksToRange());
        pulses.removeIf(pulse -> pulse.ageAt(gameTime) > pace.ticksToRange());
        lastId++;
        Pulse started = new Pulse(lastId, position, gameTime);
        pulses.add(started);
        return started;
    }

    /**
     * Drops every caster's pulses whose fronts have landed, each server
     * tick: no step hears a hold end, so a hold let go with a front still
     * out would leave its pulses standing until the caster held again.
     * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
     *
     * @param gameTime the game time
     */
    public void dropLanded(long gameTime) {
        live.entrySet().removeIf(caster -> {
            int landed = landsAfter.getOrDefault(caster.getKey(), 0);
            caster.getValue().removeIf(pulse -> pulse.ageAt(gameTime) > landed);
            boolean gone = caster.getValue().isEmpty();
            if (gone) {
                landsAfter.remove(caster.getKey());
            }
            return gone;
        });
    }

    /**
     * The pulses a caster holds out now, as the last tick of its hold left them.
     * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
     *
     * @param caster the caster
     * @return how many
     */
    public int liveFor(UUID caster) {
        return live.getOrDefault(caster, List.of()).size();
    }

    /**
     * One pulse: the front it sends from where the caster stood as it started.
     *
     * @param id        names the pulse to the caster's client
     * @param origin    where the caster stood as it started
     * @param startedAt the game time it started
     */
    public record Pulse(int id, Vec3 origin, long startedAt) {

        /**
         * The pulse's tick at a game time.
         *
         * @param gameTime the game time
         * @return its tick, 1 on the tick it started
         */
        public int ageAt(long gameTime) {
            return (int) (gameTime - startedAt) + 1;
        }
    }
}
