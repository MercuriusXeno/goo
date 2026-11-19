package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.data.StoredRemainderLedger;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.GooDeposit;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mercuriusxeno.goo.registry.GooCapabilities;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.OptionalInt;
import java.util.function.Consumer;

/**
 * An ability's exact price charged to a player: the player's remainder pays first, the whole mB the rest comes to
 * leaves the inventory, and the part of a mB overpaid banks in the remainder, so a cost of 12 1/2 takes 13 then 12.
 * A refund credits the exact price back the same way, so a charge and its refund leave the holdings and the
 * remainder as they stood.
 * decision goo-is-an-int-and-only-the-remainder-is-a-fraction
 * decision every-producer-consumer-and-container-holds-a-remainder-buffer
 * decision knives-pay-per-knife-as-each-forms
 */
final class AbilityCharges {

    private AbilityCharges() {
    }

    /**
     * What a price is paid from and refunded to: a remainder ledger and the whole mB beside it.
     */
    interface Purse {

        /**
         * @return the remainder ledger, which pays first and banks the part of a mB
         */
        IRemainderLedger ledger();

        /**
         * @param type the goo type
         * @return the whole mB held of the type
         */
        int holdings(ResourceKey<GooTypeDefinition> type);

        /**
         * Takes whole mB out.
         *
         * @param type the goo type
         * @param mb   the whole mB
         */
        void withdraw(ResourceKey<GooTypeDefinition> type, int mb);

        /**
         * Puts whole mB back.
         *
         * @param type the goo type
         * @param mb   the whole mB
         */
        void deposit(ResourceKey<GooTypeDefinition> type, int mb);
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
        return charge(purseOf(player), type, price);
    }

    /**
     * Charges the price where the purse covers it, and nothing where not.
     *
     * @param purse the purse paying
     * @param type  the goo type the price is paid in
     * @param price the exact price
     * @return whether the price was charged
     */
    static boolean charge(Purse purse, ResourceKey<GooTypeDefinition> type, GooFraction price) {
        OptionalInt whole = purse.ledger().debitExact(type, price, purse.holdings(type));
        whole.ifPresent(mb -> purse.withdraw(type, mb));
        return whole.isPresent();
    }

    /**
     * Returns a price the player paid.
     *
     * @param player the player refunded
     * @param type   the goo type the price was paid in
     * @param price  the exact price returned
     */
    static void refund(Player player, ResourceKey<GooTypeDefinition> type, GooFraction price) {
        refund(purseOf(player), type, price);
    }

    /**
     * Returns a price the purse paid.
     *
     * @param purse the purse refunded
     * @param type  the goo type the price was paid in
     * @param price the exact price returned
     */
    static void refund(Purse purse, ResourceKey<GooTypeDefinition> type, GooFraction price) {
        int whole = purse.ledger().creditExact(type, price);
        if (whole > 0) {
            purse.deposit(type, whole);
        }
    }

    /**
     * Charges the price where the payer the spray names covers it, and nothing where not.
     *
     * @param player   the paying player
     * @param paysFrom where the spray pays from
     * @param type     the goo type the price is paid in
     * @param price    the exact price
     * @return whether the price was charged
     */
    static boolean charge(Player player, PaysFrom paysFrom, ResourceKey<GooTypeDefinition> type, GooFraction price) {
        return charge(paysFrom == PaysFrom.SPRAYER_CANISTER ? new SprayerPurse(player) : purseOf(player), type, price);
    }

    private static Purse purseOf(Player player) {
        return new PlayerPurse(player);
    }

    /**
     * A sprayer's purse: the canister fitted in the sprayer the player holds, its own remainder paying first,
     * so nothing else the player carries pays; a player holding no sprayer holds nothing to pay with.
     * decision sprayer-holds-a-canister-sideways-like-a-caulk-gun
     *
     * @param player the player holding the sprayer
     */
    private record SprayerPurse(Player player) implements Purse {

        @Override
        public IRemainderLedger ledger() {
            return new StoredRemainderLedger(() -> CanisterItem.getFluidContent(fitted()).remainders(),
                    remainders -> refit(canister -> CanisterItem.setFluidContent(canister,
                            CanisterItem.getFluidContent(canister).withRemainders(remainders))));
        }

        @Override
        public int holdings(ResourceKey<GooTypeDefinition> type) {
            return CanisterItem.getFluidContent(fitted()).volumeOf(type);
        }

        @Override
        public void withdraw(ResourceKey<GooTypeDefinition> type, int mb) {
            refit(canister -> CanisterItem.removeGoo(canister, type, mb));
        }

        @Override
        public void deposit(ResourceKey<GooTypeDefinition> type, int mb) {
            int[] homeless = {mb};
            refit(canister -> homeless[0] -= CanisterItem.addGoo(canister, type, mb));
            if (homeless[0] > 0) {
                player.drop(GooStacks.createForOutput(type, homeless[0]), false);
            }
        }

        private ItemStack fitted() {
            return SprayerItem.fittedCanister(SprayerItem.heldSprayer(player));
        }

        /**
         * Changes a copy of the fitted canister and fits it back into the held sprayer.
         *
         * @param change the change to the canister
         */
        private void refit(Consumer<ItemStack> change) {
            ItemStack sprayer = SprayerItem.heldSprayer(player);
            ItemStack canister = SprayerItem.fittedCanister(sprayer);
            if (canister.isEmpty()) {
                return;
            }
            change.accept(canister);
            SprayerItem.fitCanister(sprayer, canister);
        }
    }

    /**
     * A player's purse: the player's remainder, the goo the inventory carries, and the refund deposited back
     * into it, any that finds no home dropped at the player's feet.
     *
     * @param player the player
     */
    private record PlayerPurse(Player player) implements Purse {

        @Override
        public IRemainderLedger ledger() {
            return GooCapabilities.remainderLedgerOf(player);
        }

        @Override
        public int holdings(ResourceKey<GooTypeDefinition> type) {
            return AbilityCharges.holdings(player, type);
        }

        @Override
        public void withdraw(ResourceKey<GooTypeDefinition> type, int mb) {
            GooSourceScanner.deplete(player, type, mb);
        }

        @Override
        public void deposit(ResourceKey<GooTypeDefinition> type, int mb) {
            int homeless = GooDeposit.intoInventory(player, ItemStack.EMPTY).deposit(type, mb);
            if (homeless > 0) {
                player.drop(GooStacks.createForOutput(type, homeless), false);
            }
        }
    }

    private static int holdings(Player player, ResourceKey<GooTypeDefinition> type) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(type, 0);
    }
}
