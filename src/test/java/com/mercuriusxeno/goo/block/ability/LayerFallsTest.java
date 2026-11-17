package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The falling layers a server carries: each lands on its arrival tick, one
 * whose landing refuses it stays held and lands on a later tick, and a flush,
 * as a server stop or a chunk unload calls, lands every fall it reaches at
 * once, so no fall ends unlanded (decisions falling-layer-lands-when-its-drip-arrives,
 * goo-moving-downward-falls-as-drips).
 */
class LayerFallsTest {

    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION,
            Identifier.withDefaultNamespace("overworld"));
    private static final long ARRIVES = 20;

    private final LayerFalls falls = new LayerFalls();
    private final VolumeCellBlock goo = mock(VolumeCellBlock.class);
    private final LayerFalls.Fall fall = new LayerFalls.Fall(OVERWORLD, new BlockPos(4, 10, 4), goo, ARRIVES);
    private final List<LayerFalls.Fall> landed = new ArrayList<>();

    /** A landing that takes every fall it is offered. */
    private final LayerFalls.Landing takes = new LayerFalls.Landing() {
        @Override
        public boolean land(LayerFalls.Fall arriving) {
            landed.add(arriving);
            return true;
        }

        @Override
        public void landNow(LayerFalls.Fall arriving) {
            landed.add(arriving);
        }
    };

    /** A landing with no room for any fall. */
    private final LayerFalls.Landing refuses = new LayerFalls.Landing() {
        @Override
        public boolean land(LayerFalls.Fall arriving) {
            return false;
        }

        @Override
        public void landNow(LayerFalls.Fall arriving) {
            landed.add(arriving);
        }
    };

    @Test
    void aFallInFlightLandsNothingBeforeItsArrivalTick() {
        falls.start(fall);
        falls.tick(OVERWORLD, ARRIVES - 1, takes);

        assertTrue(landed.isEmpty());
        assertEquals(List.of(fall), falls.held());
    }

    @Test
    void aFallLandsOnItsArrivalTick() {
        falls.start(fall);
        falls.tick(OVERWORLD, ARRIVES, takes);

        assertEquals(List.of(fall), landed);
        assertFalse(falls.hasPending());
    }

    @Test
    void aFallItsLandingRefusesStaysHeldAndLandsOnceTheLandingTakesIt() {
        falls.start(fall);
        falls.tick(OVERWORLD, ARRIVES, refuses);
        assertEquals(List.of(fall), falls.held());

        falls.tick(OVERWORLD, ARRIVES + 1, takes);
        assertEquals(List.of(fall), landed);
        assertFalse(falls.hasPending());
    }

    @Test
    void aFlushLandsEveryFallAtOnce() {
        LayerFalls.Fall later = new LayerFalls.Fall(OVERWORLD, new BlockPos(40, 10, 40), goo, ARRIVES * 2);
        falls.start(fall);
        falls.start(later);
        falls.flush(refuses);

        assertEquals(List.of(fall, later), landed);
        assertFalse(falls.hasPending());
    }

    @Test
    void aChunkFlushLandsOnlyTheFallsStartingInThatChunk() {
        LayerFalls.Fall elsewhere = new LayerFalls.Fall(OVERWORLD, new BlockPos(400, 10, 400), goo, ARRIVES);
        falls.start(fall);
        falls.start(elsewhere);
        falls.flushChunk(OVERWORLD, ChunkPos.containing(fall.start()), refuses);

        assertEquals(List.of(fall), landed);
        assertEquals(List.of(elsewhere), falls.held());
    }
}
