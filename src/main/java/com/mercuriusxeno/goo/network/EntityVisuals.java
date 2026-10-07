package com.mercuriusxeno.goo.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends a visual about an entity to every client that draws it: the players
 * watching its chunk, and the entity itself when it is a player, each only
 * when its client holds the payload's channel. A player without the channel,
 * a gametest's mock player among them, sees no visual rather than refusing
 * the send and failing the sender.
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
        sendToTrackers(entity, payload);
        if (entity instanceof ServerPlayer player && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * Sends the visual to the other players watching the entity's chunk
     * whose clients hold the payload's channel.
     *
     * @param entity  the entity the visual draws on
     * @param payload the visual's payload
     */
    public static void sendToTrackers(Entity entity, CustomPacketPayload payload) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(entity.chunkPosition(), false)) {
            if (player != entity && player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
