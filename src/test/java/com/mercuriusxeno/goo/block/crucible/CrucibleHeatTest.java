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
    private static final FuelGrade UNSTABLE = new FuelGrade(
            GooTypes.UNSTABLE, GooConfig.DEFAULT_UNSTABLE_TICKS_PER_MB, GooConfig.DEFAULT_UNSTABLE_MELT_RATE);
    private static final List<FuelGrade> GRADES = List.of(BLAZE);
    private static final List<FuelGrade> BURN_ORDER = List.of(UNSTABLE, BLAZE);
    private static final int DRAIN = GooConfig.DEFAULT_COMBO_DRAIN_PER_TICK;

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
     * @param heat   the heat
     * @param grades the fuel grades in burn order
     * @param pool   the pool's volume per type, drained in place
     * @param stock  the reservoir, receiving the drained goo
     */
    private static void meltTick(CrucibleHeat heat, List<FuelGrade> grades,
                                 Map<ResourceKey<GooTypeDefinition>, Integer> pool, MapStock stock) {
        GooContents contents = new GooContents(pool);
        int rate = heat.burnMeltTick(contents.totalVolume() > 0, grades, DRAIN, stock);
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
                meltTick(heat, GRADES, pool, stock);
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

            assertEquals(20, heat.burnMeltTick(true, GRADES, DRAIN, stock));
            assertEquals(3, heat.heatTicks());
            assertEquals(0, stock.volume(GooTypes.BLAZE));
        }

        /** A cold crucible with no fuel goo melts nothing. */
        @Test
        void coldWithoutFuelMeltsNothing() {
            CrucibleHeat heat = new CrucibleHeat();
            assertEquals(0, heat.burnMeltTick(true, GRADES, DRAIN, new MapStock()));
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
                heat.burnMeltTick(false, GRADES, DRAIN, stock);
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

    }

    @Nested
    class Forecast {

        /** Blaze bought heat and blaze stock sum into one burn; rock and an empty grade add none. */
        @Test
        void boughtHeatJoinsItsOwnFuelsStock() {
            CrucibleHeat heat = new CrucibleHeat();
            heat.set(12, BLAZE);
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 50).with(GooTypes.ROCK, 100);
            assertEquals(List.of(new CrucibleHeat.FuelBurn(List.of(BLAZE), 212)),
                    heat.forecast(BURN_ORDER, DRAIN, stock::volume));
        }

        /** Heat bought with blaze never counts toward unstable's burn. */
        @Test
        void boughtHeatStaysWithItsFuel() {
            CrucibleHeat heat = new CrucibleHeat();
            heat.set(3, BLAZE);
            MapStock stock = new MapStock().with(GooTypes.UNSTABLE, 10);
            assertEquals(List.of(new CrucibleHeat.FuelBurn(List.of(UNSTABLE), 10),
                            new CrucibleHeat.FuelBurn(List.of(BLAZE), 3)),
                    heat.forecast(BURN_ORDER, DRAIN, stock::volume));
        }

        /** Both fuels forecast the combo first, then the stock the combo leaves the other fuel. */
        @Test
        void comboBurnsFirstThenTheRemainder() {
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 400).with(GooTypes.UNSTABLE, 40);
            assertEquals(List.of(new CrucibleHeat.FuelBurn(BURN_ORDER, 20),
                            new CrucibleHeat.FuelBurn(List.of(BLAZE), 1440)),
                    new CrucibleHeat().forecast(BURN_ORDER, DRAIN, stock::volume));
        }

        /** A short last combo tick counts as a combo tick and leaves the short fuel nothing. */
        @Test
        void shortLastComboTickCountsWhole() {
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 3).with(GooTypes.UNSTABLE, 50);
            assertEquals(List.of(new CrucibleHeat.FuelBurn(BURN_ORDER, 2),
                            new CrucibleHeat.FuelBurn(List.of(UNSTABLE), 46)),
                    new CrucibleHeat().forecast(BURN_ORDER, DRAIN, stock::volume));
        }

        /** Heat bought with blaze alone joins blaze's remainder after the combo. */
        @Test
        void boughtHeatJoinsTheRemainderAfterTheCombo() {
            CrucibleHeat heat = new CrucibleHeat();
            heat.set(3, BLAZE);
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 4).with(GooTypes.UNSTABLE, 2);
            assertEquals(List.of(new CrucibleHeat.FuelBurn(BURN_ORDER, 1),
                            new CrucibleHeat.FuelBurn(List.of(BLAZE), 11)),
                    heat.forecast(BURN_ORDER, DRAIN, stock::volume));
        }
    }

    @Nested
    class LoneFuel {

        /** Blaze alone melts 20 mB per tick and burns 1 mB over 4 melt ticks. */
        @Test
        void blazeAloneBurnsAtItsStandingRate() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 100);
            for (int tick = 0; tick < 4; tick++) {
                assertEquals(20, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            }
            assertEquals(99, stock.volume(GooTypes.BLAZE));
        }

        /** Unstable alone melts 200 mB per tick and burns 4 mB over 4 melt ticks. */
        @Test
        void unstableAloneBurnsAtItsStandingRate() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.UNSTABLE, 100);
            for (int tick = 0; tick < 4; tick++) {
                assertEquals(200, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            }
            assertEquals(96, stock.volume(GooTypes.UNSTABLE));
        }

        /** Heat bought from blaze burns at blaze's rate to its end when unstable arrives with no blaze left. */
        @Test
        void rateSwitchesOnlyAtTheMbBoundary() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 1);
            assertEquals(20, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            stock.insert(GooTypes.UNSTABLE, 1);
            for (int tick = 0; tick < 3; tick++) {
                assertEquals(20, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            }
            assertEquals(200, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
        }
    }

    @Nested
    class Combo {

        /** Both fuels standing burn 2 mB of each per melt tick and melt 20 x 200 = 4000 mB. */
        @Test
        void bothFuelsBurnTwoOfEachAndMeltTheProduct() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 100).with(GooTypes.UNSTABLE, 200);
            assertEquals(4000, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            assertEquals(98, stock.volume(GooTypes.BLAZE));
            assertEquals(198, stock.volume(GooTypes.UNSTABLE));
        }

        /** A drain of 3 burns 3 mB of each per melt tick. */
        @Test
        void comboBurnsTheConfiguredDrain() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 100).with(GooTypes.UNSTABLE, 200);
            assertEquals(4000, heat.burnMeltTick(true, BURN_ORDER, 3, stock));
            assertEquals(97, stock.volume(GooTypes.BLAZE));
            assertEquals(197, stock.volume(GooTypes.UNSTABLE));
        }

        /** A last tick with half the blaze drain and the whole unstable drain melts half of 4000. */
        @Test
        void shortLastTickMeltsInProportion() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 1).with(GooTypes.UNSTABLE, 50);
            assertEquals(2000, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            assertEquals(0, stock.volume(GooTypes.BLAZE));
            assertEquals(48, stock.volume(GooTypes.UNSTABLE));
        }

        /** Both fuels short by half melt a quarter of 4000. */
        @Test
        void bothFuelsShortMultiplyTheirShares() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 1).with(GooTypes.UNSTABLE, 1);
            assertEquals(1000, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
        }

        /** Heat bought with blaze alone waits while the combo burns. */
        @Test
        void boughtHeatWaitsForTheCombo() {
            CrucibleHeat heat = new CrucibleHeat();
            heat.set(3, BLAZE);
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 10).with(GooTypes.UNSTABLE, 10);
            assertEquals(4000, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            assertEquals(3, heat.heatTicks());
        }

        /** A tick with nothing to melt burns no combo fuel. */
        @Test
        void idleTickBurnsNoCombo() {
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 10).with(GooTypes.UNSTABLE, 10);
            assertEquals(0, new CrucibleHeat().burnMeltTick(false, BURN_ORDER, DRAIN, stock));
            assertEquals(10, stock.volume(GooTypes.BLAZE));
        }
    }

    @Nested
    class ConfigDefaults {

        /** The spark, blaze and unstable numbers default to the idea's values. */
        @Test
        void fuelNumbersDefaultToTheIdea() {
            assertEquals(20, GooConfig.SPARK_HEAT_TICKS.getDefault());
            assertEquals(4, GooConfig.BLAZE_TICKS_PER_MB.getDefault());
            assertEquals(20, GooConfig.BLAZE_MELT_RATE.getDefault());
            assertEquals(1, GooConfig.UNSTABLE_TICKS_PER_MB.getDefault());
            assertEquals(200, GooConfig.UNSTABLE_MELT_RATE.getDefault());
            assertEquals(2, GooConfig.COMBO_DRAIN_PER_TICK.getDefault());
        }
    }
}
