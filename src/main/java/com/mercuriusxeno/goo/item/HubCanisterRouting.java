package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * Routes goo across the canisters a hub item nests: canisters already holding
 * the type first, empty canisters second, the rule SlottedCanisterData applies
 * to a placed hub (decision hub-item-blob-insert).
 */
final class HubCanisterRouting {

    private HubCanisterRouting() { }

    /**
     * One canister the routing can offer goo to.
     */
    interface RoutableCanister {
        /**
         * Returns true when the canister holds no fluid.
         *
         * @return true if empty
         */
        boolean isEmpty();

        /**
         * Returns true when the canister holds the given goo type.
         *
         * @param type the goo type
         * @return true if the canister's fluid is that type
         */
        boolean holds(ResourceKey<GooTypeDefinition> type);

        /**
         * Adds up to the given volume, capped at the canister's own capacity.
         *
         * @param type   the goo type
         * @param volume the volume offered, in mB
         * @return the volume accepted
         */
        int addGoo(ResourceKey<GooTypeDefinition> type, int volume);
    }

    /**
     * Routes the volume across the canisters, type match first, then empty.
     *
     * @param canisters the nested canisters, in slot order
     * @param type      the goo type offered
     * @param volume    the volume offered, in mB
     * @return the volume accepted across all canisters
     */
    static int route(List<? extends RoutableCanister> canisters,
            ResourceKey<GooTypeDefinition> type, int volume) {
        int left = routePass(canisters, type, volume, true);
        left = routePass(canisters, type, left, false);
        return volume - left;
    }

    /**
     * Offers the remaining volume to each canister one pass admits.
     *
     * @param canisters  the nested canisters, in slot order
     * @param type       the goo type offered
     * @param remaining  the volume still to place, in mB
     * @param matchPass  true for the type-match pass, false for the empty pass
     * @return the volume still unplaced
     */
    private static int routePass(List<? extends RoutableCanister> canisters,
            ResourceKey<GooTypeDefinition> type, int remaining, boolean matchPass) {
        int left = remaining;
        for (RoutableCanister canister : canisters) {
            if (left <= 0) { break; }
            boolean eligible = matchPass ? canister.holds(type) : canister.isEmpty();
            if (eligible) {
                left -= canister.addGoo(type, left);
            }
        }
        return left;
    }
}
