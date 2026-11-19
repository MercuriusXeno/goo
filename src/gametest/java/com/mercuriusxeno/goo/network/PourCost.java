package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.PourTickWatch;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Counts what a pour costs inside one test's bounds: the block state writes
 * landing a volume cell, read off NeoForge's neighbour notify event, which
 * every write carrying UPDATE_NEIGHBORS fires; the pour ticks volume cells
 * run, read off PourTickWatch; and the server ticks from the first pour tick
 * to the last write. Counts repeat exactly run to run, where wall-clock time
 * on a shared machine does not.
 * goo-pour-cost-is-measured-before-and-after-the-network
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class PourCost {

    /** The counts watching now, each filtering to its own test's bounds. */
    private static final List<PourCost> WATCHING = new CopyOnWriteArrayList<>();
    private static final long NONE_YET = -1;

    private final GameTestHelper helper;
    private final AABB bounds;
    private final Runnable stopTicks;
    private int writes;
    private int pourTicks;
    private long firstPour = NONE_YET;
    private long lastWrite = NONE_YET;

    private PourCost(GameTestHelper helper) {
        this.helper = helper;
        this.bounds = helper.getBounds();
        this.stopTicks = PourTickWatch.add(this::pourTickRan);
    }

    /**
     * Starts counting a test's pour from now on.
     *
     * @param helper the gametest helper
     * @return the counts
     */
    static PourCost watch(GameTestHelper helper) {
        PourCost cost = new PourCost(helper);
        WATCHING.add(cost);
        return cost;
    }

    /**
     * Hands each write landing a volume cell to every count whose bounds hold it.
     *
     * @param event the neighbour notify a block write fires
     */
    @SubscribeEvent
    public static void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getLevel().isClientSide() || !(event.getState().getBlock() instanceof VolumeCellBlock)) {
            return;
        }
        for (PourCost cost : WATCHING) {
            cost.wrote(event.getPos());
        }
    }

    /**
     * Whether no volume cell in the bounds was written for longer than a stretch, after one was.
     *
     * @param stretch the ticks without a write that stand for rest
     * @return true once the pour stands at rest
     */
    boolean restedFor(int stretch) {
        return lastWrite != NONE_YET && now() - lastWrite > stretch;
    }

    /**
     * Stops counting, so the counts read afterwards stand as the pour's.
     */
    void stop() {
        WATCHING.remove(this);
        stopTicks.run();
    }

    int writes() {
        return writes;
    }

    int pourTicks() {
        return pourTicks;
    }

    /**
     * The server ticks from the first pour tick to the last write.
     *
     * @return the ticks, 0 where nothing poured
     */
    long ticksToRest() {
        return firstPour == NONE_YET || lastWrite == NONE_YET ? 0 : lastWrite - firstPour;
    }

    private void wrote(BlockPos pos) {
        if (holds(pos)) {
            writes++;
            lastWrite = now();
        }
    }

    private void pourTickRan(BlockPos pos) {
        if (holds(pos)) {
            pourTicks++;
            if (firstPour == NONE_YET) {
                firstPour = now();
            }
        }
    }

    private boolean holds(BlockPos pos) {
        return bounds.contains(Vec3.atCenterOf(pos));
    }

    private long now() {
        return helper.getLevel().getGameTime();
    }
}
