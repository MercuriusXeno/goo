package com.mercuriusxeno.goo.ability.tartarus;

import net.minecraft.core.BlockPos;
import java.util.Optional;
import java.util.function.Function;

/**
 * A Tartarus hold as its first tick resolved it, before the hold drains
 * anything: carried to the spot its placement answered, or refused where
 * the gate leads nowhere or no safe spot stands, a refused hold charging
 * nothing.
 * decision tartarus-sets-you-on-the-nearest-safe-floor-at-the-scaled-spot-or-refuses
 *
 * @param arrival where the hold carries its caster, empty where it is refused
 */
public record TartarusCast(Optional<TartarusArrival> arrival) {

    /**
     * Resolves a hold.
     *
     * @param destination where the gate leads, empty where it leads nowhere
     * @param placement   the spot a destination's search answers, empty where none stands
     * @param yaw         the way the caster faces, in degrees
     * @return the cast
     */
    public static TartarusCast resolve(Optional<TartarusDestination> destination,
                                       Function<TartarusDestination, Optional<BlockPos>> placement, float yaw) {
        return new TartarusCast(destination.flatMap(to -> placement.apply(to)
                .map(spot -> new TartarusArrival(to, spot, yaw))));
    }

    /**
     * @return true where the hold is refused
     */
    public boolean refused() {
        return arrival.isEmpty();
    }

    /**
     * What one tick of the hold charges.
     *
     * @param share the tick's share of the price, in mB
     * @return the share, or nothing for a refused hold
     */
    public int charged(int share) {
        return refused() ? 0 : share;
    }
}
