package com.mercuriusxeno.goo.ability.petrify;

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
     */
    record Exposure(BlockState toward, float share, long lastExposed) {
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
        Exposed key = new Exposed(level.dimension(), pos.immutable());
        Exposure before = exposures.get(key);
        float start = before != null && before.toward() == toward ? before.share() : 0f;
        float share = Math.min(1f, start + amount);
        if (share >= 1f) {
            exposures.remove(key);
            return 1f;
        }
        exposures.put(key, new Exposure(toward, share, level.getGameTime()));
        send(level, pos, toward, share);
        return share;
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
            if (level == null) {
                each.remove();
                continue;
            }
            Exposure exposure = entry.getValue();
            Exposure after = decayed(exposure, level.getGameTime());
            if (after == exposure) {
                continue;
            }
            send(level, entry.getKey().pos(), exposure.toward(), after == null ? 0f : after.share());
            if (after == null) {
                each.remove();
            } else {
                entry.setValue(after);
            }
        }
    }

    /**
     * One tick of decay on a share.
     *
     * @param exposure the share standing
     * @param now      the game time
     * @return the same share while something reached it lately, null once it falls to its floor
     */
    static Exposure decayed(Exposure exposure, long now) {
        if (now - exposure.lastExposed() <= DECAY_DELAY_TICKS) {
            return exposure;
        }
        float left = exposure.share() - DECAY_PER_TICK;
        return left <= 0f ? null : new Exposure(exposure.toward(), left, exposure.lastExposed());
    }

    /** Drops every share, as a server stop does. */
    public void clear() {
        exposures.clear();
    }

    private static void send(ServerLevel level, BlockPos pos, BlockState toward, float share) {
        ChunkWatchers.send(level, pos, new BlockExposurePayload(pos, Block.getId(toward), share));
    }
}
