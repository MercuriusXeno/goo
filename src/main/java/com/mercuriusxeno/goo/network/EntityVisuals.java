package com.mercuriusxeno.goo.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends a visual about an entity to every client that draws it: the players
 * viewing its chunk, and the entity itself when it is a player whose client holds
 * the payload's channel. A player without the channel, a gametest's mock
 * player among them, sees no visual rather than refusing the send.
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
        ServerPlayer self = entity instanceof ServerPlayer player ? player : null;
        if (entity.level() instanceof ServerLevel level) {
            ChunkViewerSends.send(level, entity.blockPosition(), payload, self);
        }
        if (self != null && self.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(self, payload);
        }
    }
}
