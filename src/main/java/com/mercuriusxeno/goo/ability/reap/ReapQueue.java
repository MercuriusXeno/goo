package com.mercuriusxeno.goo.ability.reap;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.BlockAfterimagePayload;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The plants a Reap swell has yet to reach, each reaped on the tick the swell
 * reaches it, nearest first, leaving the shared afterimage of how it stood.
 * The queue lives half a second and is never saved: a swell cut short by a
 * restart leaves its far plants standing ripe.
 * reap-breeze-harvests-and-replants
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class ReapQueue {

    /** The ticks a reaped plant's afterimage takes to fade. */
    static final int AFTERIMAGE_TICKS = 12;

    private static final Map<ServerLevel, List<Pending>> PENDING = new WeakHashMap<>();

    private ReapQueue() {
    }

    /**
     * A plant waiting for the swell.
     *
     * @param pos   its cell
     * @param dueAt the game tick the swell reaches it
     */
    record Pending(BlockPos pos, long dueAt) {
    }

    /**
     * Queues a plant for the tick the swell reaches it.
     *
     * @param level the server level
     * @param pos   the plant's cell
     * @param dueAt the game tick the swell reaches it
     */
    public static void schedule(ServerLevel level, BlockPos pos, long dueAt) {
        PENDING.computeIfAbsent(level, key -> new ArrayList<>()).add(new Pending(pos.immutable(), dueAt));
    }

    /**
     * Reaps each queued plant whose tick has come.
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Pending> pending = PENDING.get(level);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<Pending> due = pending.stream().filter(plant -> plant.dueAt() <= now).toList();
        pending.removeAll(due);
        due.forEach(plant -> reapWithAfterimage(level, plant.pos()));
    }

    /**
     * Reaps a plant and leaves the afterimage of how it stood.
     *
     * @param level the server level
     * @param pos   the plant's cell
     */
    static void reapWithAfterimage(ServerLevel level, BlockPos pos) {
        BlockState stood = level.getBlockState(pos);
        if (Reaping.reap(level, pos)) {
            BlockVisuals.sendToWatchers(level, pos,
                    new BlockAfterimagePayload(pos, Block.getId(stood), GooTypes.LEAF, AFTERIMAGE_TICKS));
        }
    }
}
