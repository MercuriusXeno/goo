package com.mercuriusxeno.goo.block.tap;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds tap drips in flight until the server tick they land on, drained from
 * the server tick the way the blob throw's pending effects are.
 */
public final class TapDripScheduler {

    /**
     * Drips in flight, in the order they left their taps.
     */
    private static final List<PendingDrip> PENDING = new ArrayList<>();

    private TapDripScheduler() {
    }

    /**
     * Queues a drip to land after its fall.
     *
     * @param drip the drip in flight
     */
    static void enqueue(PendingDrip drip) {
        PENDING.add(drip);
    }

    /**
     * @return a copy of the drips in flight
     */
    public static List<PendingDrip> pending() {
        return List.copyOf(PENDING);
    }

    /**
     * Lands every drip whose arrival tick has come. A drip whose level
     * belongs to a server no longer running is dropped. The client drip
     * particle draws its own splat on reaching the surface, so a landing
     * sends none.
     *
     * @param server the ticking server
     */
    public static void drainArrived(MinecraftServer server) {
        int currentTick = server.getTickCount();
        PENDING.removeIf(drip -> drip.level().getServer() != server || currentTick >= drip.arrivalTick());
    }

    /**
     * A drip in flight from a tap to its landing.
     *
     * @param level       the level it falls in
     * @param tapPos      the tap it left
     * @param landingPos  the block it lands on
     * @param face        the landing block's face it strikes
     * @param type        the goo type it carries
     * @param arrivalTick the server tick it lands on
     */
    public record PendingDrip(ServerLevel level, BlockPos tapPos, BlockPos landingPos,
                              Direction face, ResourceKey<GooTypeDefinition> type, int arrivalTick) {
    }
}
