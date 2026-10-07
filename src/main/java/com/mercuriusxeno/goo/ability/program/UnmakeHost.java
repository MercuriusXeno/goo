package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * A host whose blocks an unmake works (capability {@link HostCapability#UNMAKE}):
 * the blocks it holds this run, what the crucible would make of each, how
 * long the unmake has worked each, and the acts of showing and finishing a
 * block's dissolve. A stream's channel holds every block its cone sees, a
 * tap's drip the block it lands on.
 * decision unmake-waves-dissolve-by-crucible-cost
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
     * Counts this run's work on a held block, a tick of a stream's hold or a
     * tap's drip, and answers the work done on it without a break.
     *
     * @param pos the held block
     * @return the work done, 1 on the first
     */
    int countUnmakeWork(BlockPos pos);

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
}
