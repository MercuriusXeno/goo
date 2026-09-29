package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Map;

/**
 * Utility class for sending goo value sync packets to players.
 * Provides methods for single-player and broadcast sends.
 */
public final class GooValueSync {

    /**
     * Log message for single-player sync.
     */
    private static final String LOG_SENT = "Sent {} goo values to {}";
    /**
     * Log message for broadcast sync.
     */
    private static final String LOG_BROADCAST = "Broadcast {} goo values to all players";

    private GooValueSync() {
    }

    /**
     * Sends the full effective value map to a single player.
     *
     * @param player the target player
     */
    public static void sendToPlayer(ServerPlayer player) {
        GooValueSyncPayload payload = buildPayload(player.level().getServer());
        PacketDistributor.sendToPlayer(player, payload);
        if (Goo.LOGGER.isDebugEnabled()) {
            Goo.LOGGER.debug(LOG_SENT, payload.values().size(), player.getName().getString());
        }
    }

    /**
     * Sends the full effective value map to all connected players.
     *
     * @param server the running server instance
     */
    public static void sendToAll(MinecraftServer server) {
        GooValueSyncPayload payload = buildPayload(server);
        PacketDistributor.sendToAllPlayers(payload);
        if (Goo.LOGGER.isInfoEnabled()) {
            Goo.LOGGER.info(LOG_BROADCAST, payload.values().size());
        }
    }

    /**
     * Builds the sync payload from the server's current effective values.
     *
     * @param server the server whose values are sent
     * @return the constructed sync payload
     */
    private static GooValueSyncPayload buildPayload(MinecraftServer server) {
        Map<Identifier, GooValue> values = GooValues.of(server).getEffectiveValues();
        return new GooValueSyncPayload(values);
    }
}
