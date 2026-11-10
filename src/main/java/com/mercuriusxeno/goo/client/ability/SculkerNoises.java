package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalInt;

/**
 * Joins what a Sculker hears to the vibrations the server forwards, so one
 * noise rings once at its rank. Only sounds ring; a vibration lends its
 * rank to the sound beside it and rings nothing on its own. A sound the
 * mod's sub-rank table names rings at once at that rank. Any other sound
 * waits a few ticks for a vibration within two blocks of it, the one its
 * noise sets off on the server, and rings at that vibration's frequency as
 * soon as they meet, from the tick it was heard; a sound no vibration meets
 * in time rings at 5. A vibration waits as long for its sound, since the
 * server's vibration and the client's sound reach the client in either
 * order.
 * decision sculker-echo-intensity-follows-vibration-rank
 */
public final class SculkerNoises {

    /** The ticks a sound waits for its vibration, and a vibration for its sound. */
    static final int WAIT_TICKS = 3;
    /** The rank a sound rings at that neither the table nor a vibration ranks, as the operator ruled it. */
    static final int UNRANKED = 5;
    /** How near a vibration must play to a sound to be that sound's, in blocks. */
    private static final double PAIR_REACH = 2.0;

    private final SculkerSoundRanks ranks;
    private final Ringer ringer;
    private final List<Noise> sounds = new ArrayList<>();
    private final List<Noise> vibrations = new ArrayList<>();

    /**
     * @param ranks  the sub-rank table
     * @param ringer what a noise rings through once its rank is known
     */
    public SculkerNoises(SculkerSoundRanks ranks, Ringer ringer) {
        this.ranks = ranks;
        this.ringer = ringer;
    }

    /** What a noise rings through once its rank is known. */
    @FunctionalInterface
    public interface Ringer {

        /**
         * Rings a noise.
         *
         * @param at      where its sound played
         * @param heardAt the game time its sound was heard, which its rings set out from
         * @param rank    its rank, 1 to 15
         */
        void ring(Vec3 at, long heardAt, int rank);
    }

    /**
     * A sound or a vibration waiting for its other half.
     *
     * @param at   where it played
     * @param tick the game time it reached the client
     * @param rank a vibration's frequency, or nothing for a sound
     */
    private record Noise(Vec3 at, long tick, int rank) {

        boolean beside(Vec3 other) {
            return at.distanceTo(other) <= PAIR_REACH;
        }
    }

    /**
     * Hears a sound: rings it at once where the table ranks it or a waiting
     * vibration meets it, and otherwise holds it for its vibration.
     *
     * @param sound the sound's id
     * @param at    where it played
     * @param tick  the game time it was heard
     */
    public void sound(String sound, Vec3 at, long tick) {
        OptionalInt subRank = ranks.rankOf(sound);
        if (subRank.isPresent()) {
            ringer.ring(at, tick, subRank.getAsInt());
            return;
        }
        Noise vibration = takeBeside(vibrations, at);
        if (vibration == null) {
            sounds.add(new Noise(at, tick, UNRANKED));
        } else {
            ringer.ring(at, tick, vibration.rank());
        }
    }

    /**
     * Takes a vibration the server forwards: the waiting sound beside it
     * rings at its frequency, and where none waits it holds for its sound.
     *
     * @param at   where it went off
     * @param tick the game time it reached the client
     * @param rank its vibration frequency
     */
    public void vibration(Vec3 at, long tick, int rank) {
        Noise sound = takeBeside(sounds, at);
        if (sound == null) {
            vibrations.add(new Noise(at, tick, rank));
        } else {
            ringer.ring(sound.at(), sound.tick(), rank);
        }
    }

    /**
     * Rings at 5 each sound whose vibration never came, and lets go each
     * vibration whose sound never came.
     *
     * @param now the game time
     */
    public void advance(long now) {
        for (Iterator<Noise> waiting = sounds.iterator(); waiting.hasNext(); ) {
            Noise sound = waiting.next();
            if (now - sound.tick() > WAIT_TICKS) {
                waiting.remove();
                ringer.ring(sound.at(), sound.tick(), UNRANKED);
            }
        }
        vibrations.removeIf(vibration -> now - vibration.tick() > WAIT_TICKS);
    }

    /** Forgets every noise waiting, as the brew's end does. */
    public void clear() {
        sounds.clear();
        vibrations.clear();
    }

    private static @Nullable Noise takeBeside(List<Noise> waiting, Vec3 at) {
        for (Iterator<Noise> each = waiting.iterator(); each.hasNext(); ) {
            Noise noise = each.next();
            if (noise.beside(at)) {
                each.remove();
                return noise;
            }
        }
        return null;
    }
}
