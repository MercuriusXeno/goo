package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The watchers of the pour ticks volume cells run, which a harness adds to
 * count the pour's cost at the cell paying it. Outside a harness no watcher
 * stands, so a pour tick costs one read of an empty list.
 * goo-pour-cost-is-measured-before-and-after-the-network
 */
public final class PourTickWatch {

    private static final List<Consumer<BlockPos>> WATCHERS = new CopyOnWriteArrayList<>();

    private PourTickWatch() {
    }

    /**
     * Hands a watcher every cell that runs a pour tick from now on.
     *
     * @param watcher handed the cell each pour tick runs at
     * @return removes the watcher
     */
    public static Runnable add(Consumer<BlockPos> watcher) {
        WATCHERS.add(watcher);
        return () -> WATCHERS.remove(watcher);
    }

    /**
     * Tells every watcher a cell ran a pour tick.
     *
     * @param cell the cell that ran it
     */
    static void ran(BlockPos cell) {
        for (Consumer<BlockPos> watcher : WATCHERS) {
            watcher.accept(cell);
        }
    }
}
