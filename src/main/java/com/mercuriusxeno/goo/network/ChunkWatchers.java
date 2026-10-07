package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends a payload about a block to every player whose view holds its chunk;
 * a listener that never negotiated the payload's channel, a gametest's mock
 * player among them, gets none rather than failing the send.
 */
public final class ChunkWatchers {

    private ChunkWatchers() {
    }

    /**
     * Sends the payload to the players tracking the block's chunk.
     *
     * @param level   the server level the block stands in
     * @param pos     the block
     * @param payload the payload
     */
    public static void send(ServerLevel level, BlockPos pos, CustomPacketPayload payload) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        for (ServerPlayer player : level.players()) {
            if (player.getChunkTrackingView().contains(chunkX, chunkZ) && player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
