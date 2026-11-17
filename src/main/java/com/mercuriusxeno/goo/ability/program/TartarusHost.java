package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.tartarus.TartarusArrival;
import java.util.Optional;

/**
 * A host over a channeling player whom Tartarus' gate can carry to the nether.
 * decision tartarus-summons-a-gate-to-the-nether-like-end-and-zone
 */
public interface TartarusHost extends ChannelHost {

    /**
     * Where the player's hold carries them, resolved on its first tick.
     *
     * @return the arrival, empty where the hold is refused
     */
    Optional<TartarusArrival> tartarusArrival();

    /**
     * Stands the gate the hold has conjured so far before the player, for
     * them and every player tracking them to see.
     *
     * @param held   the hold's tick count
     * @param charge the ticks the charge takes
     */
    void conjureTartarusGate(int held, int charge);

    /**
     * Carries the player through the gate onto the spot their hold resolved.
     *
     * @param arrival where the hold carries them
     */
    void carryThroughTartarusGate(TartarusArrival arrival);
}
