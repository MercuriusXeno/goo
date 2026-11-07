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
        pulses.removeIf(pulse -> pulse.ageAt(gameTime) > pace.ticksToRange());
        if (pace.startsPulse(held)) {
            lastId++;
            pulses.add(new Pulse(lastId, position, gameTime));
        }
        if (pulses.isEmpty()) {
            live.remove(caster);
        }
        return List.copyOf(pulses);
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
