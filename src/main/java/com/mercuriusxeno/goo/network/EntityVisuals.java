package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
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
        sendToSelf(entity, payload);
    }

    /**
     * Sends a visual to a player's own client alone, when it holds the payload's channel.
     *
     * @param entity  the entity whose client draws the visual; any other than a player sees none
     * @param payload the visual's payload
     */
    public static void sendToSelf(Entity entity, CustomPacketPayload payload) {
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

    /**
     * Sends a visual at a point to every player watching the point's chunk
     * whose client holds the payload's channel; a frost nova's ring reaches
     * its watchers this way wherever it pulses from
     * (decision nova-ring-grows-with-the-hold).
     *
     * @param level   the level
     * @param point   where the visual plays
     * @param payload the visual's payload
     */
    public static void sendToWatchersOf(ServerLevel level, Vec3 point, CustomPacketPayload payload) {
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(BlockPos.containing(point)),
                false)) {
            if (player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
