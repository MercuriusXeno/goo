package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import java.util.OptionalInt;

/**
 * An ability's exact price charged to a player: the player's remainder pays first, the whole mB the rest comes to
 * leaves the inventory, and the part of a mB overpaid banks in the remainder, so a cost of 12 1/2 takes 13 then 12.
 * decision goo-is-an-int-and-only-the-remainder-is-a-fraction
 * decision every-producer-consumer-and-container-holds-a-remainder-buffer
 */
final class AbilityCharges {

    private AbilityCharges() {
    }

    /**
     * @param player the paying player
     * @param type   the goo type the price is paid in
     * @param price  the exact price
     * @return whether the player's remainder and holdings cover it, changing nothing
     */
    static boolean affords(Player player, ResourceKey<GooTypeDefinition> type, GooFraction price) {
        return new RemainderLedger(GooCapabilities.remainderLedgerOf(player).remainders())
                .debitExact(type, price, holdings(player, type)).isPresent();
    }

    /**
     * Charges the price where the player covers it, and nothing where not.
     *
     * @param player the paying player
     * @param type   the goo type the price is paid in
     * @param price  the exact price
     * @return whether the price was charged
     */
    static boolean charge(Player player, ResourceKey<GooTypeDefinition> type, GooFraction price) {
        IRemainderLedger ledger = GooCapabilities.remainderLedgerOf(player);
        OptionalInt whole = ledger.debitExact(type, price, holdings(player, type));
        whole.ifPresent(mb -> GooSourceScanner.deplete(player, type, mb));
        return whole.isPresent();
    }

    private static int holdings(Player player, ResourceKey<GooTypeDefinition> type) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(type, 0);
    }
}
