package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * A host whose blocks an unmake works (capability {@link HostCapability#UNMAKE}):
 * the blocks it holds this run, what the crucible would make of each, how
 * long the unmake has worked each, and the acts of showing and finishing a
 * block's dissolve: a tap's drip holds the block it lands on.
 * decision unmake-drip-dissolves-the-block-below
 */
public interface UnmakeHost extends StepHost {

    /**
     * The blocks the unmake works this run.
     *
     * @return the held blocks, empty when the host holds none
     */
    List<BlockPos> unmadeBlocks();

    /**
     * The goo the crucible would melt a held block into.
     *
     * @param pos the held block
     * @return the block's goo value, or null when it holds none
     */
    @Nullable GooValue unmadeValue(BlockPos pos);

    /**
     * Counts this run's work on a held block, a tick of a stream's hold or
     * a tap's drip, and answers the work done on it so far.
     *
     * @param pos    the held block
     * @param needed the work the block takes to melt
     * @return the work done, at least 1
     */
    int countUnmakeWork(BlockPos pos, int needed);

    /**
     * Shows a held block dissolving to its viewers.
     *
     * @param pos      the held block
     * @param fraction the share dissolved, from 0 whole to 1 gone
     */
    void showUnmaking(BlockPos pos, float fraction);

    /**
     * Removes a held block and drops the goo it yields.
     *
     * @param pos   the held block
     * @param yield the goo the block leaves behind
     */
    void unmake(BlockPos pos, GooContents yield);

    /**
     * The unstable fuel's melt exponent, which an unmake's time follows as
     * the unstable crucible's does.
     *
     * @return the exponent
     */
    default double meltExponent() {
        return GooConfig.UNSTABLE_MELT_EXPONENT.get();
    }
}
