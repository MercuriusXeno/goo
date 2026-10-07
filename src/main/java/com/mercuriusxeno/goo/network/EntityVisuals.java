package com.mercuriusxeno.goo.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends a visual about an entity to every client that draws it: the players
 * whose view holds its chunk, and the entity itself when it is a player. A
 * player whose connection never negotiated the payload's channel, a
 * gametest's mock player among them, sees no visual rather than failing the
 * send for everyone.
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
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos chunk = entity.chunkPosition();
        for (ServerPlayer watcher : level.players()) {
            boolean watches = watcher == entity || watcher.getChunkTrackingView().contains(chunk.x(), chunk.z());
            if (watches && watcher.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(watcher, payload);
            }
        }
    }
}
