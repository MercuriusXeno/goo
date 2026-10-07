package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import org.jspecify.annotations.Nullable;

/**
 * A host holding one block an unmake works on (capability
 * {@link HostCapability#UNMAKE}): what the crucible would make of the block,
 * how long the unmake has worked it, and the acts of showing and finishing
 * the dissolve.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public interface UnmakeHost extends StepHost {

    /**
     * The goo the crucible would melt the held block into.
     *
     * @return the block's goo value, or null when the block holds none
     */
    @Nullable GooValue unmadeValue();

    /**
     * How long the unmake has worked the held block without a break: held
     * ticks for a stream, drips for a tap.
     *
     * @return the work done, 1 on the first
     */
    int unmakeProgress();

    /**
     * Shows the held block dissolving to its viewers.
     *
     * @param fraction the share dissolved, from 0 whole to 1 gone
     */
    void showUnmaking(float fraction);

    /**
     * Removes the held block and drops the goo it yields.
     *
     * @param yield the goo the block leaves behind
     */
    void unmake(GooContents yield);
}
