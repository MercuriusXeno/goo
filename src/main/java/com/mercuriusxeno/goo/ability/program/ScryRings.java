package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The rings instant Scry casts, every caster's, on the server: a press
 * starts one ring where the caster stands and pings as it leaves, and each
 * server tick after carries every ring's front on at its pace with no key
 * held, striking what it crossed and sending the caster's client its radius,
 * until it reaches its range. A press while an earlier ring travels starts a
 * second beside it.
 * decision scry-is-instant-and-no-longer-channeled
 */
public final class ScryRings {

    /** The server's rings, every caster's. */
    public static final ScryRings SERVER = new ScryRings(HeldSweepPulses.SERVER);

    private final HeldSweepPulses pulses;
    private final List<Ring> rings = new ArrayList<>();

    /**
     * @param pulses the pulses each ring's front stands among, which the caster's live count reads
     */
    public ScryRings(HeldSweepPulses pulses) {
        this.pulses = pulses;
    }

    /**
     * A press: starts a ring where the caster stands, pings, and carries its
     * front through its first tick.
     *
     * @param caster the caster
     * @param pace   the sweep's pace
     */
    public void cast(RingCaster caster, HeldSweepPace pace) {
        HeldSweepPulses.Pulse pulse = pulses.start(caster.id(), caster.position(), caster.gameTime(), pace);
        Ring ring = new Ring(pulse, pace, caster);
        caster.ping();
        ring.carryTo(caster.gameTime());
        if (!ring.landed()) {
            rings.add(ring);
        }
    }

    /**
     * One server tick: carries every ring's front on to its caster's game
     * time, and lets go each ring that landed or whose caster left.
     */
    public void tick() {
        rings.removeIf(ring -> {
            if (ring.caster.gone()) {
                return true;
            }
            ring.carryTo(ring.caster.gameTime());
            return ring.landed();
        });
    }

    /**
     * The caster's side of a ring: where it stands, its level's clock, and
     * what a ring does for it as it leaves and as its front travels.
     */
    public interface RingCaster {

        /** @return the caster's id */
        UUID id();

        /** @return where the caster stands */
        Vec3 position();

        /** @return the game time in the caster's level */
        long gameTime();

        /** @return true once the caster left the level it cast in */
        boolean gone();

        /** Sounds the ping a ring makes as it leaves. */
        void ping();

        /**
         * Runs the ring's steps on what its front crossed between two radii.
         *
         * @param pulse    the ring's pulse
         * @param previous the front's radius before
         * @param reached  its radius now
         */
        void strike(HeldSweepPulses.Pulse pulse, double previous, double reached);

        /**
         * Sends the caster's client the ring's radius.
         *
         * @param pulse    the ring's pulse
         * @param gameTime the game time
         */
        void send(HeldSweepPulses.Pulse pulse, long gameTime);
    }

    /** One ring: its pulse, its pace, its caster, and the tick of its pulse its front last reached. */
    private static final class Ring {

        private final HeldSweepPulses.Pulse pulse;
        private final HeldSweepPace pace;
        private final RingCaster caster;
        private int reachedAge;

        private Ring(HeldSweepPulses.Pulse pulse, HeldSweepPace pace, RingCaster caster) {
            this.pulse = pulse;
            this.pace = pace;
            this.caster = caster;
        }

        /**
         * Carries the front on to a game time, once a tick however often it
         * is asked, striking what it crossed and sending its radius.
         *
         * @param gameTime the game time in the caster's level
         */
        private void carryTo(long gameTime) {
            int age = Math.min(pulse.ageAt(gameTime), pace.ticksToRange());
            if (age <= reachedAge) {
                return;
            }
            caster.strike(pulse, pace.radiusAt(reachedAge), pace.radiusAt(age));
            caster.send(pulse, gameTime);
            reachedAge = age;
        }

        private boolean landed() {
            return reachedAge >= pace.ticksToRange();
        }
    }
}
