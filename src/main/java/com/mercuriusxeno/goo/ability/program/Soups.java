package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.SoupPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's Unmake soup: the ball held before them, the blocks streaming
 * into it, and the goo it has drunk. Each tick a block's siphon ends, the block
 * goes and its goo joins the soup; once the hold has ended and every started
 * block has streamed in, the ball turns into goo items where it hung.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class Soups {

    /** Ticks a soup may go unheld before its hold has ended, past the stream's batching. */
    public static final int HOLD_GRACE_TICKS = 5;
    /** The ball's squat as it turns into the goo items: round. */
    private static final float ROUND = 1f;
    private static final double HALF = 0.5;

    /**
     * One block streaming into a soup.
     *
     * @param goo   the goo it streams in
     * @param start the game time it started
     * @param end   the game time it is done
     */
    public record Siphon(GooContents goo, long start, long end) {
    }

    /**
     * One player's soup.
     */
    public static final class Soup {
        private final ServerLevel level;
        private final Map<BlockPos, Siphon> siphons = new LinkedHashMap<>();
        private GooContents drunk = GooContents.EMPTY;
        private Vec3 ball;
        private long lastHeld;
        private long nextStart;

        private Soup(ServerLevel level, Vec3 ball, long now) {
            this.level = level;
            this.ball = ball;
            this.lastHeld = now;
            this.nextStart = now;
        }

        /**
         * @param now the game time
         * @return whether the soup may start another block now
         */
        public boolean ready(long now) {
            return now >= nextStart;
        }

        /**
         * @param pos a block
         * @return whether it is already streaming into this soup
         */
        public boolean siphoning(BlockPos pos) {
            return siphons.containsKey(pos);
        }

        /**
         * Starts a block streaming in.
         *
         * @param pos       the block
         * @param siphon    its goo and its times
         * @param nextStart the game time the soup may start another
         */
        public void start(BlockPos pos, Siphon siphon, long nextStart) {
            siphons.put(pos.immutable(), siphon);
            this.nextStart = nextStart;
        }

        /**
         * @return the goo the soup holds
         */
        public GooContents drunk() {
            return drunk;
        }

        /**
         * @return the ball's radius
         */
        public double radius() {
            return SoupBall.radius(drunk.totalVolume());
        }

        /**
         * Holds the soup this tick, its ball moved to where it now hangs.
         *
         * @param now  the game time
         * @param here where the ball now hangs
         */
        void holdAt(long now, Vec3 here) {
            lastHeld = now;
            ball = here;
        }

        /**
         * @param now the game time
         * @return whether the hold has gone on within the grace
         */
        boolean heldAt(long now) {
            return now - lastHeld <= HOLD_GRACE_TICKS;
        }

        /**
         * @return where the ball hangs
         */
        Vec3 ball() {
            return ball;
        }

        /**
         * @return the level the soup is in
         */
        ServerLevel level() {
            return level;
        }

        /**
         * Ends each siphon that is done: its block goes and its goo joins the soup.
         *
         * @param now the game time
         * @return whether a block is still streaming in
         */
        boolean finishDone(long now) {
            Iterator<Map.Entry<BlockPos, Siphon>> done = siphons.entrySet().iterator();
            while (done.hasNext()) {
                Map.Entry<BlockPos, Siphon> siphon = done.next();
                if (siphon.getValue().end() <= now) {
                    done.remove();
                    if (level.getBlockState(siphon.getKey()).is(GooBlocks.MELTING_BLOCK.get())) {
                        level.removeBlock(siphon.getKey(), false);
                    }
                    drunk = drunk.mergeWith(siphon.getValue().goo());
                }
            }
            return !siphons.isEmpty();
        }

        /**
         * @return the blocks streaming in, as the payload shows them
         */
        List<SoupPayload.Streaming> streaming() {
            List<SoupPayload.Streaming> streaming = new ArrayList<>();
            siphons.forEach((pos, siphon) -> streaming.add(new SoupPayload.Streaming(pos, siphon.start(),
                    siphon.end())));
            return streaming;
        }
    }

    private final Map<UUID, Soup> soups = new HashMap<>();

    /**
     * Holds a player's soup this tick, starting it on the hold's first tick,
     * and moves its ball before them.
     *
     * @param player the channeling player
     * @return the soup
     */
    public Soup hold(ServerPlayer player) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        Soup soup = soups.computeIfAbsent(player.getUUID(), ignored -> new Soup(level, player.getEyePosition(), now));
        soup.holdAt(now, SoupBall.place(level, player.getEyePosition(), player.getLookAngle(), soup.radius()).center());
        return soup;
    }

    /**
     * @param player a player
     * @return their soup, or null while they hold none
     */
    public @Nullable Soup of(ServerPlayer player) {
        return soups.get(player.getUUID());
    }

    /**
     * Ends each siphon that is done, shows every soup to its viewers, and
     * turns each soup whose hold has ended, with nothing left streaming in,
     * into goo items.
     *
     * @param server the server
     * @param drops  the morphs that drop the goo items
     */
    public void tick(MinecraftServer server, UnmakeDrops drops) {
        Iterator<Map.Entry<UUID, Soup>> entries = soups.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Soup> entry = entries.next();
            Soup soup = entry.getValue();
            long now = soup.level().getGameTime();
            boolean streaming = soup.finishDone(now);
            boolean held = soup.heldAt(now);
            boolean released = !held && !streaming;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                EntityVisuals.sendToWatchers(player, new SoupPayload(player.getId(), !released, held, soup.ball(),
                        soup.drunk().getAll(), soup.streaming()));
            }
            if (released) {
                entries.remove();
                double radius = soup.radius();
                drops.unmade(soup.level(), soup.ball().subtract(0, radius, 0), (float) (radius / HALF), ROUND,
                        soup.drunk());
            }
        }
    }

    /** Forgets every soup, as a server stop does. */
    public void clear() {
        soups.clear();
    }
}
