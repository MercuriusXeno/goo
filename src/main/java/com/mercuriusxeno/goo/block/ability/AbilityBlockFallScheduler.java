package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.network.GooFlightPayload;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Schedules deferred ability block re-placements after a support block
 * breaks. The marker is removed immediately; after the flight animation
 * completes, a new marker is placed at the landing position carrying the
 * state the old one held. Each server holds one, so its falls end with the
 * server (decision type-package-and-per-server-holders).
 */
public final class AbilityBlockFallScheduler {

    /**
     * Block center offset for flight start/end positions.
     */
    private static final double BLOCK_CENTER = 0.5;
    /**
     * Sentinel for "no target entity" in GooFlightPayload.
     */
    private static final int NO_ENTITY = -1;
    /**
     * Block update flags: notify neighbors + send to clients.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;

    private final List<PendingFall> pendingFalls = new ArrayList<>();

    /**
     * Initiates a ability block fall: broadcasts a flight animation and
     * schedules re-placement at the landing pos. The caller removes the
     * marker.
     *
     * @param level       the server level
     * @param oldPos      the position being vacated
     * @param landingPos  the position to re-place at
     * @param markerBlock the ability block block instance
     * @param snapshot    the marker's state, taken before removal
     */
    public void scheduleFall(ServerLevel level, BlockPos oldPos, BlockPos landingPos,
                                    Block markerBlock, AbilityBlockSnapshot snapshot) {
        double distance = oldPos.distManhattan(landingPos);
        GooTypeDefinition definition = GooTypes.definition(level.registryAccess(), snapshot.gooType());
        int travelTicks = (int) ThrowArc.travelTicks(distance, definition.levity(), definition.baseFlightTime());

        broadcastFlight(level, oldPos, landingPos, snapshot, travelTicks);

        int arrivalTick = level.getServer().getTickCount() + travelTicks;
        enqueue(new PendingFall(arrivalTick, level, landingPos, markerBlock, snapshot));
    }

    /**
     * Queues a fall to place its marker on its arrival tick.
     *
     * @param fall the pending fall
     */
    public void enqueue(PendingFall fall) {
        pendingFalls.add(fall);
    }

    /**
     * Called each server tick to place markers whose fall animation
     * has completed. Wire to ServerTickEvent.Post.
     *
     * @param currentTick the current server tick count
     */
    public void drainArrivedFalls(int currentTick) {
        List<PendingFall> ready = new ArrayList<>();
        Iterator<PendingFall> it = pendingFalls.iterator();
        while (it.hasNext()) {
            PendingFall pf = it.next();
            if (currentTick >= pf.arrivalTick) {
                ready.add(pf);
                it.remove();
            }
        }
        for (PendingFall pf : ready) {
            placeMarker(pf);
        }
    }

    /**
     * Returns true if there are pending falls to process.
     *
     * @return true if the queue is non-empty
     */
    public boolean hasPending() {
        return !pendingFalls.isEmpty();
    }

    /**
     * Drops every pending fall, as a server stop does.
     */
    public void clear() {
        pendingFalls.clear();
    }

    /**
     * Broadcasts a goo flight payload for the falling animation, naming
     * the marker's ability so the flight renders as that ability's goo.
     *
     * @param level       the server level
     * @param oldPos      the starting position
     * @param landingPos  the landing position
     * @param snapshot    the falling marker's state
     * @param travelTicks the flight duration in ticks
     */
    private static void broadcastFlight(ServerLevel level, BlockPos oldPos, BlockPos landingPos,
                                        AbilityBlockSnapshot snapshot, int travelTicks) {
        GooFlightPayload flight = new GooFlightPayload(
                oldPos.getX() + BLOCK_CENTER,
                oldPos.getY() + BLOCK_CENTER,
                oldPos.getZ() + BLOCK_CENTER,
                GooTypes.id(snapshot.gooType()),
                NO_ENTITY,
                landingPos,
                Direction.UP.ordinal(),
                travelTicks,
                false,
                snapshot.abilityId(),
                GooThrowHandler.flightDelivery(level, snapshot.abilityId(), snapshot.gooType()));
        PacketDistributor.sendToPlayersTrackingChunk(
                level, level.getChunkAt(oldPos).getPos(), flight);
    }

    /**
     * Places a ability block block at the landing position and restores
     * the state the falling marker carried.
     *
     * @param pf the pending fall data
     */
    private static void placeMarker(PendingFall pf) {
        BlockState existing = pf.level.getBlockState(pf.landingPos);
        boolean waterlogged = existing.getFluidState().is(Fluids.WATER);
        BlockState markerState = pf.markerBlock.defaultBlockState()
                .setValue(BlockStateProperties.WATERLOGGED, waterlogged);
        pf.level.setBlock(pf.landingPos, markerState, BLOCK_UPDATE_FLAGS);
        if (pf.level.getBlockEntity(pf.landingPos) instanceof AbilityBlockEntity be) {
            be.restoreFromFall(pf.snapshot);
        }
    }

    /**
     * A ability block in mid-fall.
     *
     * @param arrivalTick the server tick it lands on
     * @param level       the level it falls in
     * @param landingPos  the position it lands at
     * @param markerBlock the ability block block
     * @param snapshot    the state the falling marker carries
     */
    public record PendingFall(int arrivalTick, ServerLevel level, BlockPos landingPos,
                              Block markerBlock, AbilityBlockSnapshot snapshot) {
    }
}
