package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * What one stream hold has marked on the blocks it reached: the blocks it
 * painted, each with the block that stood there when it was painted, which
 * keep transitioning while the hold lasts, and the positions it has stepped,
 * which it steps no further (decision decay-gnats-degrade-each-block-once).
 * A new hold starts with fresh marks.
 */
public final class HoldMarks {

    private final Map<BlockPos, Block> painted = new HashMap<>();
    private final Set<BlockPos> stepped = new HashSet<>();

    /**
     * Paints a block, unless the hold already painted or stepped it.
     *
     * @param pos    the block
     * @param origin the block standing there now
     */
    public void paint(BlockPos pos, Block origin) {
        if (!stepped.contains(pos)) {
            painted.putIfAbsent(pos.immutable(), origin);
        }
    }

    /**
     * @return the painted blocks, each with the block that stood there when painted, as a copy
     */
    public Map<BlockPos, Block> painted() {
        return Map.copyOf(painted);
    }

    /**
     * Notes that the hold stepped a block, which leaves it painted no longer.
     *
     * @param pos the block
     */
    public void noteStepped(BlockPos pos) {
        painted.remove(pos);
        stepped.add(pos.immutable());
    }

    /**
     * Drops a painted block the hold can step no further, such as one cleared to air.
     *
     * @param pos the block
     */
    public void unpaint(BlockPos pos) {
        painted.remove(pos);
    }

    /**
     * @param pos the block
     * @return true once the hold stepped it
     */
    public boolean stepped(BlockPos pos) {
        return stepped.contains(pos);
    }
}
