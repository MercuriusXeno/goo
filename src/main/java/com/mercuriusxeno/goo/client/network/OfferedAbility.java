package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.resources.Identifier;
import java.util.List;

/**
 * An ability as the radial offers it: the synced ability, and the required
 * items the player has not yet learned. While any remain, the ability stays
 * on the wheel as a locked petal the key release refuses, listing them.
 * decision locked-petal-stays-on-the-wheel
 * decision locked-petal-lists-the-unlearned-items
 *
 * @param ability   the synced ability
 * @param unlearned the required items the player does not know yet
 */
public record OfferedAbility(ClientAbility ability, List<Identifier> unlearned) {

    /**
     * Freezes the unlearned items.
     *
     * @param ability   the synced ability
     * @param unlearned the required items the player does not know yet
     */
    public OfferedAbility {
        unlearned = List.copyOf(unlearned);
    }

    /**
     * Whether the player still lacks an item the ability requires.
     *
     * @return true while any required item is unlearned
     */
    public boolean locked() {
        return !unlearned.isEmpty();
    }
}
