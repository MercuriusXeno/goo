package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * Sends a payload to every player viewing a block's chunk whose connection
 * negotiated the payload's channel; a listener that never did, such as a
 * gametest's mock player, gets none rather than refusing the send.
 */
public final class ChunkViewerSends {

    private ChunkViewerSends() {
    }

    /**
     * Sends the payload to each player viewing the chunk holding the block.
     *
     * @param level   the server level
     * @param pos     the block whose chunk the viewers see
     * @param payload the payload to send
     * @param except  a player to skip, or null to skip none
     */
    public static void send(ServerLevel level, BlockPos pos, CustomPacketPayload payload, @Nullable ServerPlayer except) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        for (ServerPlayer player : level.players()) {
            if (player != except && player.getChunkTrackingView().contains(chunkX, chunkZ)
                    && player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }
}
