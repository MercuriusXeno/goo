package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.item.GhostItem;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server side of a known items pick: a ghost of the picked item goes into the player's main hand
 * when they know the item and the hand is empty, and nothing happens otherwise.
 * decision menu-places-a-ghost-item-in-the-empty-hand
 */
public final class GhostItemPickHandler {

    private GhostItemPickHandler() {}

    /**
     * Places the ghost on the server thread.
     *
     * @param payload the pick
     * @param context the network context
     */
    public static void handle(GhostItemPickPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                place(player, payload.item());
            }
        });
    }

    /**
     * Places a ghost of the item in the player's main hand when the pick holds.
     *
     * @param player the picking player
     * @param item   the id of the item picked
     */
    public static void place(ServerPlayer player, Identifier item) {
        if (placesGhost(PlayerKnowledge.of(player), player.getMainHandItem().isEmpty(), item)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, GhostItem.of(item));
        }
    }

    /**
     * Whether a pick places a ghost: the server checks the player's knowledge again, since the
     * client's list is only what it was told.
     *
     * @param known         the items the player knows
     * @param mainHandEmpty whether the main hand holds nothing
     * @param item          the id of the item picked
     * @return true when the ghost goes into the hand
     */
    static boolean placesGhost(KnownItems known, boolean mainHandEmpty, Identifier item) {
        return mainHandEmpty && known.contains(item);
    }
}
