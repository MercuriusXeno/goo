package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.PartnerChunkPayload;
import com.mercuriusxeno.goo.network.PartnerChunksDropPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Streams the far side of a linked pair to whoever looks into it: while a
 * player's look reaches a linked portal in its level, the server sends it
 * the partner's chunk and the eight around it, however far the pair stands
 * apart, checking each every few seconds and resending it when its blocks
 * or light changed, and tells the client to drop them once the look leaves. A chunk
 * the player's own view already holds is not sent.
 * linked-partner-chunks-stream-to-any-viewer
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PartnerStreaming {

    /** How far from a portal's face a look still reaches it, in blocks. */
    private static final double REACH = 96.0;
    /** How far off the look, in degrees, a portal's face may stand and still be looked into. */
    private static final double CONE_DEGREES = 55.0;
    /** Ticks between each pass over the viewers. */
    private static final int STREAM_EVERY = 5;
    /** Ticks between each resend of the chunks a viewer holds. */
    private static final int RESEND_EVERY = 60;
    /** How many chunks out from the partner's the stream reaches: one, for the partner's and its eight neighbors. */
    private static final int RING = 1;

    /** The chunks each player was last sent. */
    private static final Map<UUID, Set<ChunkPos>> STREAMED = new HashMap<>();
    /** What each streamed chunk held when each player was last sent it, so it is resent only on change. */
    private static final Map<UUID, Map<ChunkPos, Integer>> SENT = new HashMap<>();

    private PartnerStreaming() {
    }

    /**
     * The chunks a viewer is streamed: for each linked portal in its level
     * that its look reaches, from before the face, the partner's chunk and
     * the eight around it.
     *
     * @param level the viewer's level
     * @param eye   its eye
     * @param look  its unit look
     * @param links the pairs linked
     * @return the chunks
     */
    public static Set<ChunkPos> chunksFor(ResourceKey<Level> level, Vec3 eye, Vec3 look,
            Collection<QuantumPortals.Link> links) {
        Set<ChunkPos> chunks = new HashSet<>();
        for (QuantumPortals.Link link : links) {
            for (Portal[] way : List.of(new Portal[] {link.entry(), link.exit()},
                    new Portal[] {link.exit(), link.entry()})) {
                if (way[0].block().dimension().equals(level) && looksInto(way[0].gate(), eye, look)) {
                    chunks.addAll(around(ChunkPos.containing(way[1].block().pos())));
                }
            }
        }
        return chunks;
    }

    private static boolean looksInto(Gate gate, Vec3 eye, Vec3 look) {
        Vec3 toward = gate.center().subtract(eye);
        double distance = toward.length();
        return gate.out(eye) > 0 && distance <= REACH
                && (distance == 0 || toward.dot(look) / distance >= Math.cos(Math.toRadians(CONE_DEGREES)));
    }

    private static Set<ChunkPos> around(ChunkPos center) {
        Set<ChunkPos> ring = new HashSet<>();
        for (int dx = -RING; dx <= RING; dx++) {
            for (int dz = -RING; dz <= RING; dz++) {
                ring.add(new ChunkPos(center.x() + dx, center.z() + dz));
            }
        }
        return ring;
    }

    /**
     * The chunks a player was last streamed.
     *
     * @param player the player
     * @return the chunks
     */
    public static Set<ChunkPos> streamedTo(ServerPlayer player) {
        return Set.copyOf(STREAMED.getOrDefault(player.getUUID(), Set.of()));
    }

    /**
     * Streams each player the far side it looks into, and drops what its look left.
     *
     * @param event the post-tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % STREAM_EVERY != 0) {
            return;
        }
        Collection<QuantumPortals.Link> links = QuantumPortals.get(server.overworld()).links().values();
        boolean resend = server.getTickCount() % RESEND_EVERY == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            stream(player, links, resend);
        }
        STREAMED.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        SENT.keySet().retainAll(STREAMED.keySet());
    }

    private static void stream(ServerPlayer player, Collection<QuantumPortals.Link> links, boolean resend) {
        ServerLevel level = player.level();
        Set<ChunkPos> wanted = chunksFor(level.dimension(), player.getEyePosition(), player.getLookAngle(), links);
        wanted.removeIf(pos -> player.getChunkTrackingView().contains(pos));
        Set<ChunkPos> before = STREAMED.getOrDefault(player.getUUID(), Set.of());
        Map<ChunkPos, Integer> sent = SENT.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        sent.keySet().retainAll(wanted);
        for (ChunkPos pos : wanted) {
            if (resend || !before.contains(pos)) {
                LevelChunk chunk = level.getChunk(pos.x(), pos.z());
                PartnerChunkPayload payload = PartnerChunkPayload.of(chunk, level.getLightEngine());
                Integer contents = payload.contentsHash();
                if (!contents.equals(sent.put(pos, contents))) {
                    send(player, payload);
                }
            }
        }
        List<Long> left = before.stream().filter(pos -> !wanted.contains(pos)).map(ChunkPos::pack).toList();
        if (!left.isEmpty()) {
            send(player, new PartnerChunksDropPayload(left));
        }
        STREAMED.put(player.getUUID(), wanted);
    }

    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload
            payload) {
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no stream.
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * Forgets every viewer when the server stops.
     *
     * @param event the stop event
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STREAMED.clear();
        SENT.clear();
    }
}
