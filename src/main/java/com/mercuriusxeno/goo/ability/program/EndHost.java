package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.end.EndDestination;
import java.util.Optional;

/**
 * A host over a channeling player whom End's gate can carry to the End.
 * decision end-summons-a-gate-like-zone-that-takes-you-to-the-end-and-back
 */
public interface EndHost extends ChannelHost {

    /**
     * Where the gate takes the player from where they stand.
     *
     * @return the destination, empty where it takes them nowhere
     */
    Optional<EndDestination> endDestination();

    /**
     * Stands the gate the hold has conjured so far before the player, for
     * them and every player tracking them to see.
     *
     * @param held   the hold's tick count
     * @param charge the ticks the charge takes
     */
    void conjureEndGate(int held, int charge);

    /**
     * Carries the player through the gate to its destination, where a
     * matching gate releases them.
     *
     * @param destination where the gate leads
     */
    void carryThroughEndGate(EndDestination destination);
}
