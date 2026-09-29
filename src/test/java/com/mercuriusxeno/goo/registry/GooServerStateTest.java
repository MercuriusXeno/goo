package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.block.ability.ChainMarkerFallScheduler;
import com.mercuriusxeno.goo.block.tap.TapDripScheduler;
import com.mercuriusxeno.goo.network.BlobEffectScheduler;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A server stop clears everything its state holds in flight, and a fresh
 * server's state starts empty, so nothing lands
 * against a stopped server's levels (decision type-package-and-per-server-holders).
 */
class GooServerStateTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    private static void fill(GooServerState state) {
        state.blobEffects().enqueue(new BlobEffectScheduler.PendingEffect(
                1, null, null, GooTypes.ROCK, -1, POS, Direction.UP, ""));
        state.tapDrips().enqueue(new TapDripScheduler.PendingDrip(
                null, POS.above(), POS, Direction.UP, GooTypes.ROCK, 1, 1));
        state.markerFalls().enqueue(new ChainMarkerFallScheduler.PendingFall(
                1, null, POS, null, null));
    }

    private static boolean holdsNothing(GooServerState state) {
        return !state.blobEffects().hasPending()
                && state.tapDrips().pending().isEmpty()
                && !state.markerFalls().hasPending();
    }

    @Test
    void filledStateHoldsEachEntry() {
        GooServerState state = new GooServerState();
        fill(state);

        assertTrue(state.blobEffects().hasPending());
        assertFalse(state.tapDrips().pending().isEmpty());
        assertTrue(state.markerFalls().hasPending());
    }

    @Test
    void stopClearsEveryPendingEntry() {
        GooServerState state = new GooServerState();
        fill(state);

        state.clear();

        assertTrue(holdsNothing(state));
    }

    @Test
    void freshStateStartsEmpty() {
        GooServerState stopped = new GooServerState();
        fill(stopped);

        assertTrue(holdsNothing(new GooServerState()));
    }
}
