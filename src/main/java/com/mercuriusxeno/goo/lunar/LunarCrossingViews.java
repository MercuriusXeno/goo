package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.network.LunarCrossingMovedPayload;
import com.mercuriusxeno.goo.network.LunarCrossingPayload;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/**
 * The server's side of a player's crossing of lunar's seam. As the crossing carries a player, the server notes which
 * chunks of their view it had already sent and tells their client the crossing's shift; the client moves what it holds
 * over by that shift and names the chunks it moved. When the player's view then jumps across the seam, the server
 * keeps the chunks both sides name, forgetting and sending only the rest. A client that names nothing, because a hook
 * is missing or another mod replaced one, is sent its whole landing view as any teleport is.
 * decision moon-wraps-at-its-border-like-the-zone
 */
public final class LunarCrossingViews {

    /** A view jumping further than half the moon has crossed the seam rather than walked. */
    private static final int ACROSS_THE_SEAM = LunarCrossingShift.PERIOD_CHUNKS / 2;
    private static final Map<ServerPlayer, Crossing> CROSSINGS = new WeakHashMap<>();

    private LunarCrossingViews() {
    }

    /**
     * A crossing under way: its shift, the landing chunks whose twins the server had sent before it, and the landing
     * chunks the client named as moved, null until the client names them.
     */
    private record Crossing(LunarCrossingShift shift, LongSet sent, @Nullable LongSet confirmed) {
    }

    /**
     * Notes what a player's client holds as the crossing carries them, and tells the client the crossing's shift.
     *
     * @param player the player being carried
     * @param shift  the crossing's shift
     */
    public static void carrying(ServerPlayer player, LunarCrossingShift shift) {
        LongSet pending = player.connection.chunkSender.pendingChunks;
        LongSet sent = new LongOpenHashSet();
        player.getChunkTrackingView().forEach(pos -> {
            if (!pending.contains(pos.pack())) {
                sent.add(shift.apply(pos).pack());
            }
        });
        CROSSINGS.put(player, new Crossing(shift, sent, null));
        if (player.connection.hasChannel(LunarCrossingPayload.TYPE)) {
            player.connection.send(LunarCrossingPayload.of(shift));
        }
    }

    /**
     * Notes the chunks a player's client moved by the crossing's shift.
     *
     * @param player the player whose client moved its chunks
     * @param moved  the client's naming of what it moved
     */
    public static void confirmed(ServerPlayer player, LunarCrossingMovedPayload moved) {
        Crossing crossing = CROSSINGS.get(player);
        if (crossing != null && crossing.shift().equals(moved.shift())) {
            CROSSINGS.put(player, new Crossing(crossing.shift(), crossing.sent(), moved.movedChunks()));
        }
    }

    /**
     * Tells a player's client of a view jumping across the seam, keeping what the client moved: the chunks of the
     * landing view the server had sent and the client moved draw no forget and no send.
     *
     * @param player  the player whose view jumps
     * @param from    the view they held
     * @param to      the view they now hold
     * @param onEnter vanilla's queueing of a chunk to send
     * @param onLeave vanilla's forgetting of a chunk
     * @return whether the jump was told here; false leaves it to vanilla
     */
    public static boolean differenceAcross(ServerPlayer player, ChunkTrackingView from, ChunkTrackingView to,
                                           Consumer<ChunkPos> onEnter, Consumer<ChunkPos> onLeave) {
        Crossing crossing = CROSSINGS.get(player);
        if (crossing == null || !jumpsAcrossTheSeam(from, to)) {
            return false;
        }
        CROSSINGS.remove(player);
        if (crossing.confirmed() == null) {
            return false;
        }
        Set<ChunkPos> kept = crossing.shift().moved(from, to);
        kept.removeIf(pos -> !crossing.sent().contains(pos.pack()) || !crossing.confirmed().contains(pos.pack()));
        ServerLevel level = player.level();
        from.forEach(pos -> {
            if (kept.contains(crossing.shift().apply(pos))) {
                EventHooks.fireChunkUnWatch(player, pos, level);
            } else {
                onLeave.accept(pos);
            }
        });
        to.forEach(pos -> {
            if (!kept.contains(pos)) {
                onEnter.accept(pos);
                return;
            }
            LevelChunk chunk = level.getChunkSource().chunkMap.getChunkToSend(pos.pack());
            if (chunk != null) {
                EventHooks.fireChunkWatch(player, chunk, level);
            }
        });
        return true;
    }

    private static boolean jumpsAcrossTheSeam(ChunkTrackingView from, ChunkTrackingView to) {
        return from instanceof ChunkTrackingView.Positioned last && to instanceof ChunkTrackingView.Positioned next
                && (Math.abs(next.center().x() - last.center().x()) > ACROSS_THE_SEAM
                || Math.abs(next.center().z() - last.center().z()) > ACROSS_THE_SEAM);
    }
}
