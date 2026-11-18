package com.mercuriusxeno.goo.ability.tartarus;

import com.mercuriusxeno.goo.ability.end.EndAvailability;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Whether Tartarus stands available to a player, read through the gray-out
 * seam End's uses, so the radial draws it grayed out and the server refuses
 * its channel unpaid: available in the overworld and the nether, which its
 * gate joins, grayed out anywhere else.
 * decision tartarus-in-the-nether-takes-you-to-the-overworld-at-eight-times-your-spot
 */
public final class TartarusAvailability {

    private TartarusAvailability() {
    }

    /**
     * Tartarus' availability for a player.
     *
     * @param standingIn the level the player stands in
     * @return available where the gate leads somewhere, grayed out elsewhere
     */
    public static EndAvailability of(ResourceKey<Level> standingIn) {
        return TartarusDestination.targetOf(standingIn).isPresent() ? EndAvailability.AVAILABLE
                : EndAvailability.GRAYED_OUT;
    }
}
