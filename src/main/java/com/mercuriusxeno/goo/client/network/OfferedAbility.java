package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;

/**
 * An ability as the radial offers it: the synced ability, and whether the
 * player still lacks an item it requires, which keeps it on the wheel as a
 * dimmed petal the key release refuses.
 * decision locked-petal-stays-on-the-wheel
 *
 * @param ability the synced ability
 * @param locked  true while the player does not know every item it requires
 */
public record OfferedAbility(ClientAbility ability, boolean locked) {
}
