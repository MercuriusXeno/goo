package com.mercuriusxeno.goo.ability.petrify;

import com.mercuriusxeno.goo.ability.program.BlockBreakHost;
import com.mercuriusxeno.goo.network.BlockExposurePayload;
import com.mercuriusxeno.goo.network.ChunkWatchers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * How far each block has gone toward its next calcify rung: exposure builds
 * while Petrify's fog or its tap's drips reach the block, a full share steps
 * the block a rung, its floor, and once nothing has reached it a while the
 * unfinished share decays slowly back to that floor, so a released block
 * keeps no patches. Every change goes to the clients watching the block,
 * which draw the next block mingled in by the share
 * (decisions petrify-stone-encasement-and-calcify-map,
 * petrify-drip-calcifies-and-grows-dripstone). The shares live with the
 * running server and start over when it stops.
 */
public final class BlockExposures {

    /** Ticks after the last exposure before the share starts decaying. */
    static final long DECAY_DELAY_TICKS = 10L;
    /** How much the share decays each tick once decaying: a full share recedes in four seconds. */
    static final float DECAY_PER_TICK = 1f / 80f;

    /** The tint of a mingled block drawn in its own colors. */
    public static final int UNTINTED = BlockExposurePayload.UNTINTED;

    private final Map<Exposed, Exposure> exposures = new HashMap<>();

    /**
     * A block in its dimension.
     *
     * @param dimension the level's dimension
     * @param pos       the block
     */
    private record Exposed(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /**
     * One block's progress toward the state it calcifies into.
     *
     * @param toward      the state the block becomes at a full share
     * @param share       the share built, 0 to 1
     * @param lastExposed the game time something last built it
     * @param finishRate  the share it builds each tick on its own once left to finish, 0 while it decays instead
     * @param tint        the RGB the client tints the mingled block by, {@link #UNTINTED} for none
     */
    record Exposure(BlockState toward, float share, long lastExposed, float finishRate, int tint) {

        /**
         * An untinted exposure.
         *
         * @param toward      the state the block becomes at a full share
         * @param share       the share built
         * @param lastExposed the game time something last built it
         * @param finishRate  the share it builds each tick on its own once left to finish
         */
        Exposure(BlockState toward, float share, long lastExposed, float finishRate) {
            this(toward, share, lastExposed, finishRate, UNTINTED);
        }

        /**
         * An exposure that decays once nothing reaches it.
         *
         * @param toward      the state the block becomes at a full share
         * @param share       the share built
         * @param lastExposed the game time something last built it
         */
        Exposure(BlockState toward, float share, long lastExposed) {
            this(toward, share, lastExposed, 0f, UNTINTED);
        }

        boolean finishing() {
            return finishRate > 0f;
        }
    }

    /**
     * Builds a block's exposure toward a state; progress toward another state
     * starts over.
     *
     * @param level  the server level
     * @param pos    the block
     * @param toward the state the block becomes at a full share
     * @param amount the share this exposure adds
     * @return the share after it, 1 the tick the block steps its rung and its progress clears
     */
    public float expose(ServerLevel level, BlockPos pos, BlockState toward, float amount) {
        return expose(level, pos, toward, amount, UNTINTED);
    }

    /**
     * Builds a block's exposure toward a state, the client tinting the mingled
     * block; progress toward another state starts over.
     *
     * @param level  the server level
     * @param pos    the block
     * @param toward the state the block becomes at a full share
     * @param amount the share this exposure adds
     * @param tint   the RGB the client tints the mingled block by, {@link #UNTINTED} for none
     * @return the share after it, 1 the tick the block steps its rung and its progress clears
     */
    public float expose(ServerLevel level, BlockPos pos, BlockState toward, float amount, int tint) {
        Exposed key = new Exposed(level.dimension(), pos.immutable());
        Exposure before = exposures.get(key);
        float start = before != null && before.toward() == toward ? before.share() : 0f;
        float share = Math.min(1f, start + amount);
        if (share >= 1f) {
            exposures.remove(key);
            return 1f;
        }
        exposures.put(key, new Exposure(toward, share, level.getGameTime(), 0f, tint));
        send(level, pos, toward, share, tint);
        return share;
    }

    /**
     * Leaves a block's built share to finish on its own: from now it builds
     * the rate each tick, whatever reaches it, and steps once full
     * (decision decay-gnats-degrade-each-block-once).
     *
     * @param level the server level
     * @param pos   the block
     * @param rate  the share it builds each tick
     */
    public void finishAlone(ServerLevel level, BlockPos pos, float rate) {
        exposures.computeIfPresent(new Exposed(level.dimension(), pos.immutable()),
                (key, exposure) -> new Exposure(exposure.toward(), exposure.share(), exposure.lastExposed(), rate,
                        exposure.tint()));
    }

    /**
     * Whether a block is finishing its step on its own.
     *
     * @param level the server level
     * @param pos   the block
     * @return true once {@link #finishAlone} left it to finish and until it steps
     */
    public boolean finishingAt(ServerLevel level, BlockPos pos) {
        Exposure exposure = exposures.get(new Exposed(level.dimension(), pos));
        return exposure != null && exposure.finishing();
    }

    /**
     * The share a block has built toward its next rung.
     *
     * @param level the server level
     * @param pos   the block
     * @return the share, 0 at its floor
     */
    public float shareAt(ServerLevel level, BlockPos pos) {
        Exposure exposure = exposures.get(new Exposed(level.dimension(), pos));
        return exposure == null ? 0f : exposure.share();
    }

    /**
     * Decays every share nothing has reached for a while, dropping those back at their floor.
     *
     * @param server the ticking server
     */
    public void decay(MinecraftServer server) {
        Iterator<Map.Entry<Exposed, Exposure>> each = exposures.entrySet().iterator();
        while (each.hasNext()) {
            Map.Entry<Exposed, Exposure> entry = each.next();
            ServerLevel level = server.getLevel(entry.getKey().dimension());
            if (level == null || !tickShare(level, entry)) {
                each.remove();
            }
        }
    }

    /**
     * Runs one tick of decay, or of growth for a share left to finish, on one
     * block's share, stepping the block when a finishing share fills.
     *
     * @param level the block's level
     * @param entry the block and its share, which a share still standing is written back to
     * @return false once the share is gone, at its floor or stepped
     */
    private static boolean tickShare(ServerLevel level, Map.Entry<Exposed, Exposure> entry) {
        Exposure exposure = entry.getValue();
        Exposure after = decayed(exposure, level.getGameTime());
        if (after == exposure) {
            return true;
        }
        BlockPos pos = entry.getKey().pos();
        if (after != null && after.share() >= 1f) {
            finish(level, pos, after.toward());
            return false;
        }
        send(level, pos, exposure.toward(), after == null ? 0f : after.share(), exposure.tint());
        if (after == null) {
            return false;
        }
        entry.setValue(after);
        return true;
    }

    /**
     * Steps a block left to finish on its own, unless something has since
     * cleared it to air.
     *
     * @param level  the server level
     * @param pos    the block
     * @param toward the state it becomes
     */
    private static void finish(ServerLevel level, BlockPos pos, BlockState toward) {
        if (!level.getBlockState(pos).isAir()) {
            BlockBreakHost.transform(level, pos, toward);
        }
    }

    /**
     * One tick of decay on a share, or of growth on a share left to finish.
     *
     * @param exposure the share standing
     * @param now      the game time
     * @return the same share while something reached it lately, the grown share while it finishes alone,
     *         null once it falls to its floor
     */
    static Exposure decayed(Exposure exposure, long now) {
        if (exposure.finishing()) {
            return new Exposure(exposure.toward(), Math.min(1f, exposure.share() + exposure.finishRate()),
                    exposure.lastExposed(), exposure.finishRate(), exposure.tint());
        }
        if (now - exposure.lastExposed() <= DECAY_DELAY_TICKS) {
            return exposure;
        }
        float left = exposure.share() - DECAY_PER_TICK;
        return left <= 0f ? null : new Exposure(exposure.toward(), left, exposure.lastExposed(), 0f,
                exposure.tint());
    }

    /** Drops every share, as a server stop does. */
    public void clear() {
        exposures.clear();
    }

    private static void send(ServerLevel level, BlockPos pos, BlockState toward, float share, int tint) {
        ChunkWatchers.send(level, pos, new BlockExposurePayload(pos, Block.getId(toward), share, tint));
    }
}
