package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the crucible's heat, the seam its melt tick calls: heat bought from
 * blaze goo in the reservoir and spent only on ticks that melt an item
 * (decision fuel-goo-heats-per-mb). Grades are built from GooConfig's defaults,
 * since the unit suite loads no config.
 */
class CrucibleHeatTest {

    private static final FuelGrade BLAZE = new FuelGrade(
            GooTypes.BLAZE, GooConfig.DEFAULT_BLAZE_TICKS_PER_MB, GooConfig.DEFAULT_BLAZE_MELT_RATE);
    private static final List<FuelGrade> GRADES = List.of(BLAZE);

    /** A map-backed reservoir. */
    private static final class MapStock implements CrucibleHeat.FuelStock {
        private final Map<ResourceKey<GooTypeDefinition>, Integer> held = new HashMap<>();

        MapStock with(ResourceKey<GooTypeDefinition> type, int volume) {
            held.put(type, volume);
            return this;
        }

        @Override
        public int volume(ResourceKey<GooTypeDefinition> type) {
            return held.getOrDefault(type, 0);
        }

        @Override
        public int extract(ResourceKey<GooTypeDefinition> type, int amount) {
            int taken = Math.min(amount, volume(type));
            held.put(type, volume(type) - taken);
            return taken;
        }

        void insert(ResourceKey<GooTypeDefinition> type, int amount) {
            held.put(type, volume(type) + amount);
        }
    }

    /**
     * Runs one melt tick the way CrucibleMelting does: burn heat, then drain the pool
     * at the heat's rate into the reservoir.
     *
     * @param heat the heat
     * @param pool the pool's volume per type, drained in place
     * @param stock the reservoir, receiving the drained goo
     */
    private static void meltTick(CrucibleHeat heat, Map<ResourceKey<GooTypeDefinition>, Integer> pool, MapStock stock) {
        GooContents contents = new GooContents(pool);
        int rate = heat.burnMeltTick(contents.totalVolume() > 0, GRADES, stock);
        if (rate <= 0) {
            return;
        }
        int extraction = CrucibleMath.extractionRate(contents.totalVolume(), rate);
        CrucibleMath.computeDrainShares(contents, extraction).forEach((type, share) -> {
            pool.put(type, pool.get(type) - share);
            stock.insert(type, share);
        });
    }

    @Nested
    class Melting {

        /**
         * With 100 mB blaze and a rock item, 40 melt ticks drain 800 mB of rock
         * and burn 1 mB blaze every 4 ticks, leaving 90 mB.
         */
        @Test
        void blazeBuysFourTicksPerMbAtTwentyMbPerTick() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 100);
            Map<ResourceKey<GooTypeDefinition>, Integer> pool = new HashMap<>(Map.of(GooTypes.ROCK, 2000));

            for (int tick = 0; tick < 40; tick++) {
                meltTick(heat, pool, stock);
            }

            assertEquals(1200, pool.get(GooTypes.ROCK));
            assertEquals(800, stock.volume(GooTypes.ROCK));
            assertEquals(90, stock.volume(GooTypes.BLAZE));
        }

        /** The first melt tick buys 4 ticks and spends one, leaving 3. */
        @Test
        void firstMeltTickBuysThenSpends() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 1);

            assertEquals(20, heat.burnMeltTick(true, GRADES, stock));
            assertEquals(3, heat.heatTicks());
            assertEquals(0, stock.volume(GooTypes.BLAZE));
        }

        /** A cold crucible with no fuel goo melts nothing. */
        @Test
        void coldWithoutFuelMeltsNothing() {
            CrucibleHeat heat = new CrucibleHeat();
            assertEquals(0, heat.burnMeltTick(true, GRADES, new MapStock()));
        }
    }

    @Nested
    class Idle {

        /** Heat with no meltable item stays whole across 100 server ticks. */
        @Test
        void heatWithoutAnItemIsNotSpent() {
            CrucibleHeat heat = new CrucibleHeat();
            heat.set(12, BLAZE);
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 50);

            for (int tick = 0; tick < 100; tick++) {
                heat.burnMeltTick(false, GRADES, stock);
            }

            assertEquals(12, heat.heatTicks());
            assertEquals(50, stock.volume(GooTypes.BLAZE));
        }
    }

    @Nested
    class CanHeat {

        /** A cold crucible with no fuel goo cannot heat. */
        @Test
        void coldWithoutFuelCannotHeat() {
            assertFalse(new CrucibleHeat().canHeat(GRADES, new MapStock().with(GooTypes.ROCK, 100)));
        }

        /** Blaze goo in the reservoir lets a cold crucible heat. */
        @Test
        void blazeInReservoirCanHeat() {
            assertTrue(new CrucibleHeat().canHeat(GRADES, new MapStock().with(GooTypes.BLAZE, 1)));
        }

        /** Heat ticks alone let the crucible heat. */
        @Test
        void heatTicksAloneCanHeat() {
            CrucibleHeat heat = new CrucibleHeat();
            heat.set(1, BLAZE);
            assertTrue(heat.canHeat(GRADES, new MapStock()));
        }

        /** The fuel goo volume sums every grade's fuel. */
        @Test
        void fuelVolumeCountsFuelGooOnly() {
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 50).with(GooTypes.ROCK, 100);
            assertEquals(50, CrucibleHeat.fuelVolume(GRADES, stock));
        }
    }
}
