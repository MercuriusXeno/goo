package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends a visual about a block to every client tracking its chunk whose
 * connection holds the payload's channel; a player without the channel, a
 * gametest's mock player among them, sees no visual rather than refusing
 * the send.
 * decision prism-blob-becomes-a-milky-quartz-crystal
 */
public final class BlockVisuals {

    private BlockVisuals() {}

    /**
     * Sends the visual to the players tracking the block's chunk.
     *
     * @param level   the block's level
     * @param pos     the block's position
     * @param payload the visual's payload
     */
    public static void sendToWatchers(ServerLevel level, BlockPos pos, CustomPacketPayload payload) {
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(pos), false)) {
            if (player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
