package com.mercuriusxeno.goo.ability.program;

import java.util.ArrayList;
import java.util.List;

/**
 * The sounds a running program holds until their frame, so a sound step
 * naming a frame finishes at once and the steps after it run without
 * waiting on it. Each sound plays on the host that held it, so a strike's
 * sound plays at the struck mob though the trap's program holds it. Only
 * the program's cursor persists, so a held sound is lost if the world
 * reloads during its wait.
 * ability-json-names-its-choreography
 * urchin-spikes-shink-out-and-shink-back
 */
public final class HeldCues {

    private final List<Held> held = new ArrayList<>();

    /**
     * One sound waiting for its tick.
     *
     * @param cue       the evaluated sound
     * @param ticksLeft the program ticks still to start before it plays
     * @param host      the host it plays on
     */
    private record Held(SoundCue cue, int ticksLeft, StepHost host) {
    }

    /**
     * Holds a sound until a number of program ticks have started.
     *
     * @param cue    the evaluated sound
     * @param frames the program ticks to wait, at least one
     * @param host   the host it plays on
     */
    void hold(SoundCue cue, int frames, StepHost host) {
        held.add(new Held(cue, frames, host));
    }

    /**
     * Counts one program tick starting: plays and drops every held sound
     * whose wait ends with it, in the order they were held.
     */
    void playDue() {
        List<Held> waiting = new ArrayList<>(held.size());
        for (Held next : held) {
            if (next.ticksLeft() <= 1) {
                next.host().playSound(next.cue());
            } else {
                waiting.add(new Held(next.cue(), next.ticksLeft() - 1, next.host()));
            }
        }
        held.clear();
        held.addAll(waiting);
    }
}
