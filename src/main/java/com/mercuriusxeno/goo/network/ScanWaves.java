package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.WaveHost;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The scan waves in flight on one server: each runs its ability's program at
 * its landing on the tick the drawn wave reaches it, the delay its delivery's
 * speed gives the distance from the eye, never on the throw tick
 * (decision click-is-a-hit-scan-drawn-as-a-delayed-wave).
 */
public final class ScanWaves {

    /** Log: a scan ability whose program the wave host refused at load. */
    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on its scan wave: {}";

    private final List<Wave> pending = new ArrayList<>();

    /**
     * A wave from the eye to its landing, as the throw reads it.
     *
     * @param level       the level thrown in
     * @param eye         the thrower's eye
     * @param landing     where the scan landed
     * @param delivery    the scan delivery, its speed setting the delay
     * @param ability     the thrown ability's id
     * @param steps       the program the landing runs
     * @param currentTick the server tick the use fired on
     * @return the wave, arriving the delay after the throw
     */
    public static Wave thrown(ServerLevel level, Vec3 eye, Vec3 landing, Delivery delivery, Identifier ability,
                              List<Step> steps, int currentTick) {
        return new Wave(currentTick + delivery.waveTicks(eye.distanceTo(landing)), level, landing, ability, steps);
    }

    /**
     * Queues a wave to land on its arrival tick.
     *
     * @param wave the wave
     */
    public void schedule(Wave wave) {
        pending.add(wave);
    }

    /**
     * Whether any wave is still in flight.
     *
     * @return true while the queue holds a wave
     */
    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /**
     * Runs and removes every wave whose arrival tick has come.
     *
     * @param currentTick the current server tick
     */
    public void drainArrived(int currentTick) {
        List<Wave> arrived = new ArrayList<>();
        pending.removeIf(wave -> {
            boolean due = currentTick >= wave.arrivalTick();
            if (due) {
                arrived.add(wave);
            }
            return due;
        });
        arrived.forEach(ScanWaves::land);
    }

    /**
     * Runs a wave's program once at its landing; a program the wave host
     * cannot serve is refused at load and logged with the ability.
     *
     * @param wave the wave that arrived
     */
    private static void land(Wave wave) {
        try {
            ProgramBehavior.forHost(wave.steps(), HostKind.WAVE).tick(new WaveHost(wave.level(), wave.landing()));
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, wave.ability(), e.getMessage());
        }
    }

    /**
     * Drops every wave in flight, as a server stop does.
     */
    public void clear() {
        pending.clear();
    }

    /**
     * A scan wave in flight.
     *
     * @param arrivalTick the server tick the wave reaches its landing
     * @param level       the level it lands in
     * @param landing     where it lands
     * @param ability     the thrown ability's id, which a load refusal names
     * @param steps       the program the landing runs
     */
    public record Wave(int arrivalTick, ServerLevel level, Vec3 landing, Identifier ability, List<Step> steps) {
    }
}
