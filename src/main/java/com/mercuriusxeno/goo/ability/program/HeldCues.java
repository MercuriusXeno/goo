package com.mercuriusxeno.goo.ability.program;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The sounds a running program holds until their frame, so a sound step
 * naming a frame finishes at once and the steps after it run without
 * waiting on it. Only the program's cursor persists, so a held sound is
 * lost if the world reloads during its wait.
 * ability-json-names-its-choreography
 */
public final class HeldCues {

    private final List<Held> held = new ArrayList<>();

    /**
     * One sound waiting for its tick.
     *
     * @param cue     the evaluated sound
     * @param dueTick the program tick it plays on
     */
    private record Held(SoundCue cue, int dueTick) {
    }

    /**
     * Holds a sound until a program tick.
     *
     * @param cue     the evaluated sound
     * @param dueTick the program tick it plays on
     */
    void hold(SoundCue cue, int dueTick) {
        held.add(new Held(cue, dueTick));
    }

    /**
     * Plays and drops every held sound due by this program tick, in the order they were held.
     *
     * @param host         the host the sounds play on
     * @param programTicks the program tick now starting
     */
    void playDue(StepHost host, int programTicks) {
        Iterator<Held> it = held.iterator();
        while (it.hasNext()) {
            Held next = it.next();
            if (next.dueTick() <= programTicks) {
                host.playSound(next.cue());
                it.remove();
            }
        }
    }
}
