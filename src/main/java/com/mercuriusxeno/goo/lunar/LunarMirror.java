package com.mercuriusxeno.goo.lunar;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

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
    /**
     * The other side loads wider than the view, so the landing view stands ticking, and so ready to send, before a
     * crossing reaches it: a view holds chunks two past its distance, a crossing lands a chunk beyond the twin, and a
     * ticket ticks chunks one short of its radius.
     */
    private static final int LOAD_MARGIN = 4;

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
        return labelledAt(pos, otherSideOf(level, seen), level);
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
     * Holds the other side loaded for a lunar player within view of the seam: the view they would hold standing at
     * their twin across the seam, which is the view a crossing lands them in, past the far edge included, so the
     * crossing waits on no generation. A player by a corner holds the twin across each seam and across both.
     *
     * @param player the player
     */
    public static void holdTheOtherSideLoaded(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || level.dimension() != Lunar.LEVEL) {
            return;
        }
        int reach = reachOf(level);
        ChunkPos wrapped = LunarSeam.wrap(player.chunkPosition());
        int twinX = twinShift(wrapped.x(), reach);
        int twinZ = twinShift(wrapped.z(), reach);
        if (twinX != 0) {
            hold(level, new ChunkPos(wrapped.x() + twinX, wrapped.z()), reach);
        }
        if (twinZ != 0) {
            hold(level, new ChunkPos(wrapped.x(), wrapped.z() + twinZ), reach);
        }
        if (twinX != 0 && twinZ != 0) {
            hold(level, new ChunkPos(wrapped.x() + twinX, wrapped.z() + twinZ), reach);
        }
    }

    private static int reachOf(ServerLevel level) {
        return level.getServer().getPlayerList().getViewDistance() + LOAD_MARGIN;
    }

    private static void hold(ServerLevel level, ChunkPos centre, int reach) {
        level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, centre, reach);
    }

    /**
     * How far along one axis a chunk within reach of the seam stands from its twin across it: one period, toward the
     * other edge.
     *
     * @param chunk the chunk's index along x or z, inside the moon's span
     * @param reach how far a player sees, in chunks
     * @return the move to the twin, or 0 for a chunk beyond reach of the seam
     */
    static int twinShift(int chunk, int reach) {
        if (HALF_CHUNKS - 1 - chunk < reach) {
            return -LunarCrossingShift.PERIOD_CHUNKS;
        }
        return chunk + HALF_CHUNKS < reach ? LunarCrossingShift.PERIOD_CHUNKS : 0;
    }

    private static boolean nearTheSeam(ChunkPos chunk, int reach) {
        ChunkPos wrapped = LunarSeam.wrap(chunk);
        return twinShift(wrapped.x(), reach) != 0 || twinShift(wrapped.z(), reach) != 0;
    }

    // The other side's chunk as it stands loaded; the seen chunk itself where the other side has yet to load.
    private static LevelChunk otherSideOf(ServerLevel level, LevelChunk seen) {
        ChunkPos wrapped = LunarSeam.wrap(seen.getPos());
        LevelChunk otherSide = level.getChunkSource().getChunkNow(wrapped.x(), wrapped.z());
        return otherSide == null ? seen : otherSide;
    }

    private static ClientboundLevelChunkWithLightPacket labelledAt(ChunkPos seenAt, LevelChunk source,
                                                                   ServerLevel level) {
        ClientboundLevelChunkWithLightPacket own = new ClientboundLevelChunkWithLightPacket(source,
                level.getLightEngine(), null, null);
        return new ClientboundLevelChunkWithLightPacket(seenAt.x(), seenAt.z(), own.chunkData(), own.lightData());
    }
}
