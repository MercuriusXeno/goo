package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Reads and writes the items a player knows on the server, and keeps their
 * client's copy current (decision knowledge-capability-remembers-destroyed-items).
 */
public final class PlayerKnowledge {

    private PlayerKnowledge() {
    }

    /**
     * Answers the items the player knows.
     *
     * @param player the player
     * @return the known items, empty for a player who learned none
     */
    public static KnownItems of(Player player) {
        return player.getData(GooAttachments.KNOWN_ITEMS);
    }

    /**
     * Answers the id the known-items set records an item under.
     *
     * @param item the item
     * @return the item's registry id
     */
    public static Identifier idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /**
     * Records the item as known to the player, telling their client the
     * first time they learn it.
     *
     * @param player the player who learned the item
     * @param learned the item learned
     */
    public static void learn(ServerPlayer player, Item learned) {
        learn(player, idOf(learned));
    }

    /**
     * Records the item an id names as known to the player, telling their
     * client the first time they learn it.
     *
     * @param player the player who learned the item
     * @param item   the id of the item learned
     */
    public static void learn(ServerPlayer player, Identifier item) {
        KnownItems known = of(player);
        if (known.contains(item)) {
            return;
        }
        player.setData(GooAttachments.KNOWN_ITEMS, known.with(item));
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no sync.
        if (player.connection.hasChannel(KnownItemLearnedPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new KnownItemLearnedPayload(item));
        }
    }

    /**
     * Sends the player's whole known set to their client.
     *
     * @param player the player
     */
    public static void sendToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new KnownItemsSyncPayload(of(player)));
    }
}
