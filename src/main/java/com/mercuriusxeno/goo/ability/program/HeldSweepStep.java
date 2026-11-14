package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.HeldSweepPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;

/**
 * A held step that sweeps: Scry, Glimmer and Magnet each send overlapping
 * pulses at the one shared pace, one payload for each live pulse each tick,
 * which the caster's client draws through the one held-sweep visual.
 * decision held-sweeps-share-one-fast-shell-visual
 */
public interface HeldSweepStep extends Step {

    /**
     * The sweep's pace.
     *
     * @return the pace
     */
    HeldSweepPace pace();

    /**
     * The goo type whose colors tint the sweep.
     *
     * @return the goo type
     */
    ResourceKey<GooTypeDefinition> goo();

    /**
     * Whether the sweep's fronts outline the mobs they cross on the caster's
     * client: Scry's do, the ore senses' do not.
     *
     * @return true for a sweep that paints mobs
     */
    default boolean paintsMobs() {
        return false;
    }

    /**
     * The payload drawing one live pulse on one tick.
     *
     * @param pulse    the pulse
     * @param gameTime the game time
     * @return the payload for the caster's client
     */
    default HeldSweepPayload sweepOf(HeldSweepPulses.Pulse pulse, long gameTime) {
        HeldSweepPace pace = pace();
        return new HeldSweepPayload(pulse.id(), pulse.origin(), (float) pace.radiusAt(pulse.ageAt(gameTime)),
                (float) pace.range(), goo(), paintsMobs());
    }
}
