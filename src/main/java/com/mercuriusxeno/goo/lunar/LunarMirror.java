package com.mercuriusxeno.goo.lunar;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.OptionalInt;

/**
 * The view past lunar's seam: a player seeing a chunk past the seam is sent the chunk it wraps to, labelled where they
 * see it, so the other side shows out to the server's view distance as it stands, ground, features, structures and
 * builds alike. The other side stays loaded while a player stands within view of the seam, and a change there is
 * sent again to every player seeing it from across the seam.
 * decision moon-wraps-at-its-border-like-the-zone
 */
public final class LunarMirror {

    private static final int CHUNK_SIDE = 16;
    /** The chunks from the moon's middle to either edge. */
    private static final int HALF_CHUNKS = LunarSeam.PERIOD / CHUNK_SIDE / 2;
    /** The other side loads a little wider than the view, so it stands loaded before a player sees it. */
    private static final int LOAD_MARGIN = 2;

    private LunarMirror() {
    }

    /**
     * The chunk packet a player is sent for a chunk they see: for a lunar chunk past the seam, the chunk it wraps
     * to, labelled where they see it; for every other chunk, the chunk itself.
     *
     * @param level the level the chunk stands in
     * @param seen  the chunk the player sees
     * @return the packet to send
     */
    public static ClientboundLevelChunkWithLightPacket packetFor(ServerLevel level, LevelChunk seen) {
        ChunkPos pos = seen.getPos();
        if (level.dimension() != Lunar.LEVEL || !LunarSeam.pastTheSeam(pos)) {
            return new ClientboundLevelChunkWithLightPacket(seen, level.getLightEngine(), null, null);
        }
        return labelledAt(pos, otherSideOf(level, pos), level);
    }

    /**
     * Sends a changed lunar chunk again to every player seeing it from across the seam, labelled where they see it.
     *
     * @param level    the level the chunk stands in
     * @param chunkMap the level's chunk map, which knows who sees each chunk
     * @param changed  the chunk whose blocks or light changed
     */
    public static void resendAcrossTheSeam(ServerLevel level, ChunkMap chunkMap, LevelChunk changed) {
        if (level.dimension() != Lunar.LEVEL || !nearTheSeam(changed.getPos(), reachOf(level))) {
            return;
        }
        for (ChunkPos mirror : LunarSeam.mirrorsOf(changed.getPos())) {
            for (ServerPlayer player : chunkMap.getPlayers(mirror, false)) {
                player.connection.send(labelledAt(mirror, changed, level));
            }
        }
    }

    /**
     * Holds the other side loaded for a lunar player within view of the seam: the strip in from the other edge they
     * see past the seam, and the corner across both seams where they see past two.
     *
     * @param player the player
     */
    public static void holdTheOtherSideLoaded(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Lunar.LEVEL) {
            return;
        }
        int reach = reachOf(level);
        ChunkPos standing = LunarSeam.wrap(player.chunkPosition());
        OptionalInt otherX = otherSide(standing.x(), reach);
        OptionalInt otherZ = otherSide(standing.z(), reach);
        otherX.ifPresent(x -> hold(level, new ChunkPos(x, standing.z()), reach));
        otherZ.ifPresent(z -> hold(level, new ChunkPos(standing.x(), z), reach));
        if (otherX.isPresent() && otherZ.isPresent()) {
            hold(level, new ChunkPos(otherX.getAsInt(), otherZ.getAsInt()), reach);
        }
    }

    private static int reachOf(ServerLevel level) {
        return level.getServer().getPlayerList().getViewDistance() + LOAD_MARGIN;
    }

    private static void hold(ServerLevel level, ChunkPos centre, int reach) {
        level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, centre, reach);
    }

    /**
     * Where the other side's strip centres along one axis for a chunk within reach of the seam: a reach in from the
     * far edge, so the strip covers what the player sees past the seam and stops at the edge.
     *
     * @param chunk the chunk's index along x or z, inside the moon's span
     * @param reach how far a player sees, in chunks
     * @return the strip's centre, or empty for a chunk beyond reach of the seam
     */
    static OptionalInt otherSide(int chunk, int reach) {
        if (HALF_CHUNKS - 1 - chunk < reach) {
            return OptionalInt.of(-HALF_CHUNKS + reach);
        }
        return chunk + HALF_CHUNKS < reach ? OptionalInt.of(HALF_CHUNKS - 1 - reach) : OptionalInt.empty();
    }

    private static boolean nearTheSeam(ChunkPos chunk, int reach) {
        ChunkPos wrapped = LunarSeam.wrap(chunk);
        return otherSide(wrapped.x(), reach).isPresent() || otherSide(wrapped.z(), reach).isPresent();
    }

    private static LevelChunk otherSideOf(ServerLevel level, ChunkPos pastTheSeam) {
        ChunkPos wrapped = LunarSeam.wrap(pastTheSeam);
        return level.getChunk(wrapped.x(), wrapped.z());
    }

    private static ClientboundLevelChunkWithLightPacket labelledAt(ChunkPos seenAt, LevelChunk source,
                                                                   ServerLevel level) {
        ClientboundLevelChunkWithLightPacket own = new ClientboundLevelChunkWithLightPacket(source,
                level.getLightEngine(), null, null);
        return new ClientboundLevelChunkWithLightPacket(seenAt.x(), seenAt.z(), own.chunkData(), own.lightData());
    }
}
