package com.mercuriusxeno.goo.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Sends a visual about an entity to every client that draws it: the players
 * whose view holds its chunk, and the entity itself when it is a player,
 * each only when its client holds the payload's channel. A player without
 * the channel, a gametest's mock player among them, sees no visual rather
 * than refusing the send, whether it is the entity or one watching it.
 * Decisions ailment-overlay-shader-per-ailment, afterimage-is-one-shared-effect.
 */
public final class EntityVisuals {

    private EntityVisuals() {}

    /**
     * Sends the visual to the entity's watchers and the entity itself.
     *
     * @param entity  the entity the visual draws on
     * @param payload the visual's payload
     */
    public static void sendToWatchers(Entity entity, CustomPacketPayload payload) {
        if (entity.level() instanceof ServerLevel level) {
            for (ServerPlayer watcher : level.getChunkSource().chunkMap.getPlayers(entity.chunkPosition(), false)) {
                sendIfHeard(watcher == entity ? null : watcher, payload);
            }
        }
        sendIfHeard(entity instanceof ServerPlayer player ? player : null, payload);
    }

    /**
     * Sends the payload to a player whose client holds its channel.
     *
     * @param player  the player, or null for none
     * @param payload the visual's payload
     */
    private static void sendIfHeard(@Nullable ServerPlayer player, CustomPacketPayload payload) {
        if (player != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
