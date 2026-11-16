package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.List;

/**
 * The layers of world goo a server carries while they fall through open
 * air: each left its cell already, and joins the fill below on the tick its
 * drawn drip arrives. A layer falling is goo already paid for, so no fall
 * ends unlanded: one whose landing has no room on its arrival tick stays
 * held and tries again each tick, and a flush, as a server stop or a chunk
 * unload calls, lands every fall it reaches at once.
 * falling-layer-lands-when-its-drip-arrives
 * goo-moving-downward-falls-as-drips
 */
public final class LayerFalls {

    private final List<Fall> falls = new ArrayList<>();

    /**
     * One layer falling.
     *
     * @param dimension the level it falls in
     * @param start     the open cell it falls from, whose first top below it lands on
     * @param cell      the goo's volume cell block, whose fill it joins
     * @param arrivesAt the server tick its drip arrives
     */
    public record Fall(ResourceKey<Level> dimension, BlockPos start, VolumeCellBlock cell, long arrivesAt) {
    }

    /**
     * What lands a fall in its level.
     */
    public interface Landing {

        /**
         * @param fall the fall arriving
         * @return true where the layer joined a fill, false where it found no room and waits
         */
        boolean land(Fall fall);

        /**
         * Lands a fall at once, as fill where it finds room and as its goo given back where it finds none.
         *
         * @param fall the fall
         */
        void landNow(Fall fall);
    }

    /**
     * @param fall a layer that has left its cell and falls
     */
    public void start(Fall fall) {
        falls.add(fall);
    }

    /**
     * Lands each fall in a level whose drip has arrived; one its landing refuses stays held.
     *
     * @param dimension the level ticking
     * @param now       the server tick
     * @param landing   what lands a fall in its level
     */
    public void tick(ResourceKey<Level> dimension, long now, Landing landing) {
        for (Fall fall : List.copyOf(falls)) {
            if (fall.dimension().equals(dimension) && fall.arrivesAt() <= now && landing.land(fall)) {
                falls.remove(fall);
            }
        }
    }

    /**
     * Lands every fall at once, as a server stopping does.
     *
     * @param landing what lands a fall in its level
     */
    public void flush(Landing landing) {
        List<Fall> landingNow = List.copyOf(falls);
        falls.clear();
        landingNow.forEach(landing::landNow);
    }

    /**
     * Lands at once every fall starting in a chunk, as that chunk unloading does.
     *
     * @param dimension the chunk's level
     * @param chunk     the chunk
     * @param landing   what lands a fall in its level
     */
    public void flushChunk(ResourceKey<Level> dimension, ChunkPos chunk, Landing landing) {
        List<Fall> landingNow = falls.stream().filter(fall -> fall.dimension().equals(dimension)
                && ChunkPos.containing(fall.start()).equals(chunk)).toList();
        falls.removeAll(landingNow);
        landingNow.forEach(landing::landNow);
    }

    /**
     * @return the falls still held, in the order they started
     */
    public List<Fall> held() {
        return List.copyOf(falls);
    }

    /**
     * @return true while any layer falls
     */
    public boolean hasPending() {
        return !falls.isEmpty();
    }

    /**
     * Drops every fall, as a server already stopped does.
     */
    public void clear() {
        falls.clear();
    }
}
