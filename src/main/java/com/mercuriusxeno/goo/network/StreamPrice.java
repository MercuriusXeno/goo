package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.PlacementPrice;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a stream draining on placement charges its player as each thing it
 * does happens, each price exact, the player's remainder paying first: a
 * share of the ability's cost for each thing it lays, and a step's own price
 * for each other unit.
 * goop-drains-per-cubic-meter-of-slime-placed
 * decay-pays-per-block-degraded-and-bite
 *
 * @param player  the streaming player
 * @param ability the stream ability
 */
record StreamPrice(ServerPlayer player, AbilityDefinition ability) implements PlacementPrice {

    @Override
    public boolean pay(int placementsPerCost) {
        return pay(ability.cost().exactIn(GooValues.of(player.level())).dividedBy(placementsPerCost));
    }

    @Override
    public boolean affords(GooFraction unitPrice) {
        return AbilityCharges.affords(player, ability.gooType(), unitPrice);
    }

    @Override
    public boolean pay(GooFraction unitPrice) {
        return AbilityCharges.charge(player, ability.gooType(), unitPrice);
    }
}
