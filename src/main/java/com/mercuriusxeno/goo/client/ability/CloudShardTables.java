package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * The shard layout of every crystal cloud this client draws, one per marker,
 * each drawn from a seed the client picks the first time it sees that cloud
 * and held in client memory only, so a cloud dropped and seen again draws a
 * new layout (decision cloud-seed-per-client-ephemeral).
 */
final class CloudShardTables {

    private final Map<BlockPos, ShardTable> tablesByMarker = new HashMap<>();
    private final LongSupplier seeds;

    /**
     * Tables seeded from a fresh random source.
     */
    CloudShardTables() {
        this(new Random()::nextLong);
    }

    /**
     * Tables seeded from the given source.
     *
     * @param seeds answers the seed of each new cloud
     */
    CloudShardTables(LongSupplier seeds) {
        this.seeds = seeds;
    }

    /**
     * The layout of the cloud at a marker, drawn on the first call for that marker.
     *
     * @param marker the marker the cloud stands on
     * @return the cloud's layout
     */
    ShardTable tableAt(BlockPos marker) {
        return tablesByMarker.computeIfAbsent(marker.immutable(), pos -> ShardTable.fromSeed(seeds.getAsLong()));
    }

    /**
     * Forgets the layout of a cloud that stopped rendering.
     *
     * @param marker the marker the cloud stood on
     */
    void dropAt(BlockPos marker) {
        tablesByMarker.remove(marker);
    }

    /**
     * Forgets every layout whose marker no longer holds a drawn cloud.
     *
     * @param stillCloud answers whether a marker still holds a drawn cloud
     */
    void retainClouds(Predicate<BlockPos> stillCloud) {
        tablesByMarker.keySet().removeIf(stillCloud.negate());
    }

    /**
     * Forgets every layout, as the client leaves the level.
     */
    void dropAll() {
        tablesByMarker.clear();
    }
}
