package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.ValuedStack;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the crucible's heat, the seam its melt tick calls: heat bought from
 * blaze goo in the reservoir and spent only on ticks that melt an item
 * (decision fuel-goo-heats-per-mb). Grades are built from GooConfig's defaults,
 * since the unit suite loads no config.
 */
class CrucibleHeatTest {

    private static final FuelGrade BLAZE = new FuelGrade(
            GooTypes.BLAZE, GooConfig.DEFAULT_BLAZE_TICKS_PER_MB, GooConfig.DEFAULT_BLAZE_MELT_EXPONENT);
    private static final FuelGrade UNSTABLE = new FuelGrade(
            GooTypes.UNSTABLE, GooConfig.DEFAULT_UNSTABLE_TICKS_PER_MB, GooConfig.DEFAULT_UNSTABLE_MELT_EXPONENT);
    private static final List<FuelGrade> GRADES = List.of(BLAZE);
    private static final List<FuelGrade> BURN_ORDER = List.of(UNSTABLE, BLAZE);
    private static final int DRAIN = GooConfig.DEFAULT_COMBO_DRAIN_PER_TICK;
    private static final Identifier ROCK_ITEM = Identifier.fromNamespaceAndPath("minecraft", "cobblestone");

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

        int accept(ResourceKey<GooTypeDefinition> type, int amount, boolean simulate) {
            if (!simulate) {
                insert(type, amount);
            }
            return amount;
        }
    }

    /**
     * A melt pool and the queue of items it holds, ticked the way CrucibleMelting ticks them.
     */
    private static final class Pool {
        private final CrucibleMeltQueue queue = new CrucibleMeltQueue();
        private GooContents contents = GooContents.EMPTY;

        /**
         * Inserts items carrying one goo type each.
         *
         * @param type   the goo type
         * @param volume one item's mB
         * @param count  the items
         * @return this pool
         */
        Pool with(ResourceKey<GooTypeDefinition> type, int volume, int count) {
            GooContents unit = GooContents.EMPTY.withAdded(type, volume);
            queue.appendAll(List.of(new ValuedStack(ROCK_ITEM, count, unit)));
            contents = contents.mergeWith(GooContents.EMPTY.withAdded(type, volume * count));
            return this;
        }

        int volume(ResourceKey<GooTypeDefinition> type) {
            return contents.getVolume(type);
        }
    }

    /**
     * Runs one melt tick the way CrucibleMelting does: burn heat, then advance the
     * queue's clock on the burning grade, moving goo from the pool into the reservoir.
     *
     * @param heat   the heat
     * @param grades the fuel grades in burn order
     * @param pool   the pool and its queue, advanced in place
     * @param stock  the reservoir, receiving the melted goo
     */
    private static void meltTick(CrucibleHeat heat, List<FuelGrade> grades, Pool pool, MapStock stock) {
        FuelGrade burning = heat.burnMeltTick(pool.contents.totalVolume() > 0, grades, DRAIN, stock);
        if (burning != null) {
            pool.contents = pool.queue.advanceNext(burning.meltExponent(), pool.contents, stock::accept);
        }
    }

    /**
     * Ticks one item of the given mB to its end and answers the tick it finished on,
     * asserting the reservoir held less than the whole item on every tick before.
     *
     * @param grades the fuel grades in burn order
     * @param stock  the reservoir, stocked with fuel
     * @param volume the item's mB
     * @return the tick the item finished on
     */
    private static int ticksToMelt(List<FuelGrade> grades, MapStock stock, int volume) {
        CrucibleHeat heat = new CrucibleHeat();
        Pool pool = new Pool().with(GooTypes.ROCK, volume, 1);
        int tick = 0;
        while (pool.contents.totalVolume() > 0) {
            assertTrue(stock.volume(GooTypes.ROCK) < volume);
            meltTick(heat, grades, pool, stock);
            tick++;
        }
        assertEquals(volume, stock.volume(GooTypes.ROCK));
        return tick;
    }

    /**
     * Returns the items still melting in the pool's queue.
     *
     * @param pool the pool
     * @return the item count
     */
    private static int itemsMelting(Pool pool) {
        return pool.queue.entries().stream().mapToInt(CrucibleMeltQueue.Entry::count).sum();
    }

    @Nested
    class Clock {

        /** A 1000 mB item alone under blaze finishes on tick ceil(1000 ^ 0.75) = 178. */
        @Test
        void thousandMbItemMeltsIn178TicksUnderBlaze() {
            assertEquals(178, ticksToMelt(GRADES, new MapStock().with(GooTypes.BLAZE, 1000), 1000));
        }

        /** A 1000 mB item alone under unstable finishes on tick ceil(1000 ^ 0.5) = 32. */
        @Test
        void thousandMbItemMeltsIn32TicksUnderUnstable() {
            assertEquals(32, ticksToMelt(List.of(UNSTABLE), new MapStock().with(GooTypes.UNSTABLE, 1000), 1000));
        }

        /**
         * Two 1000 mB items under lone blaze take turns: both half melted on tick 178, the first
         * finishing on tick 355 and the second on tick 356 (decision lone-fuel-advances-one-item).
         */
        @Test
        void twoItemsTakeTurnsUnderALoneFuel() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 1000);
            Pool pool = new Pool().with(GooTypes.ROCK, 1000, 2);
            List<Integer> finishedOn = new ArrayList<>();
            for (int tick = 1; pool.contents.totalVolume() > 0; tick++) {
                int before = itemsMelting(pool);
                meltTick(heat, GRADES, pool, stock);
                for (int finished = itemsMelting(pool); finished < before; finished++) {
                    finishedOn.add(tick);
                }
                if (tick == 178) {
                    assertEquals(List.of(0.5, 0.5), pool.queue.head().progress().stream()
                            .map(progress -> Math.round(progress * 1e6) / 1e6).toList());
                }
            }
            assertEquals(List.of(355, 356), finishedOn);
        }

        /** Halfway through its 178 ticks a 1000 mB item has moved half its goo, the pool holding the rest. */
        @Test
        void gooMovesInProportionAsTheClockAdvances() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 1000);
            Pool pool = new Pool().with(GooTypes.ROCK, 1000, 1);
            for (int tick = 0; tick < 89; tick++) {
                meltTick(heat, GRADES, pool, stock);
            }
            assertEquals(500, stock.volume(GooTypes.ROCK), 1);
            assertEquals(1000, stock.volume(GooTypes.ROCK) + pool.volume(GooTypes.ROCK));
        }
    }

    @Nested
    class Melting {

        /**
         * With 100 mB blaze and a 2000 mB rock item on its 300-tick clock, 40 melt ticks
         * move 266 mB of rock and burn 1 mB blaze every 4 ticks, leaving 90 mB.
         */
        @Test
        void blazeBuysFourTicksPerMbOnTheItemsClock() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 100);
            Pool pool = new Pool().with(GooTypes.ROCK, 2000, 1);

            for (int tick = 0; tick < 40; tick++) {
                meltTick(heat, GRADES, pool, stock);
            }

            assertEquals(1734, pool.volume(GooTypes.ROCK));
            assertEquals(266, stock.volume(GooTypes.ROCK));
            assertEquals(90, stock.volume(GooTypes.BLAZE));
        }

        /** The first melt tick buys 4 ticks and spends one, leaving 3. */
        @Test
        void firstMeltTickBuysThenSpends() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 1);

            assertEquals(BLAZE, heat.burnMeltTick(true, GRADES, DRAIN, stock));
            assertEquals(3, heat.heatTicks());
            assertEquals(0, stock.volume(GooTypes.BLAZE));
        }

        /** A cold crucible with no fuel goo melts nothing. */
        @Test
        void coldWithoutFuelMeltsNothing() {
            CrucibleHeat heat = new CrucibleHeat();
            assertNull(heat.burnMeltTick(true, GRADES, DRAIN, new MapStock()));
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

        /** Blaze alone melts on its own clock and burns 1 mB over 4 melt ticks. */
        @Test
        void blazeAloneBurnsOnItsOwnClock() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 100);
            for (int tick = 0; tick < 4; tick++) {
                assertEquals(BLAZE, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            }
            assertEquals(99, stock.volume(GooTypes.BLAZE));
        }

        /** Unstable alone melts on its own clock and burns 4 mB over 4 melt ticks. */
        @Test
        void unstableAloneBurnsOnItsOwnClock() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.UNSTABLE, 100);
            for (int tick = 0; tick < 4; tick++) {
                assertEquals(UNSTABLE, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            }
            assertEquals(96, stock.volume(GooTypes.UNSTABLE));
        }

        /** Heat bought from blaze burns on blaze's clock to its end when unstable arrives with no blaze left. */
        @Test
        void rateSwitchesOnlyAtTheMbBoundary() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 1);
            assertEquals(BLAZE, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            stock.insert(GooTypes.UNSTABLE, 1);
            for (int tick = 0; tick < 3; tick++) {
                assertEquals(BLAZE, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            }
            assertEquals(UNSTABLE, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
        }
    }

    @Nested
    class Combo {

        /** Both fuels standing burn 2 mB of each per melt tick and melt on unstable's clock. */
        @Test
        void bothFuelsBurnTwoOfEachOnUnstablesClock() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 100).with(GooTypes.UNSTABLE, 200);
            assertEquals(UNSTABLE, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            assertEquals(98, stock.volume(GooTypes.BLAZE));
            assertEquals(198, stock.volume(GooTypes.UNSTABLE));
        }

        /** A drain of 3 burns 3 mB of each per melt tick. */
        @Test
        void comboBurnsTheConfiguredDrain() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 100).with(GooTypes.UNSTABLE, 200);
            assertEquals(UNSTABLE, heat.burnMeltTick(true, BURN_ORDER, 3, stock));
            assertEquals(97, stock.volume(GooTypes.BLAZE));
            assertEquals(197, stock.volume(GooTypes.UNSTABLE));
        }

        /** A last tick with half the blaze drain still burns on unstable's clock, draining what stands. */
        @Test
        void shortLastTickDrainsWhatStands() {
            CrucibleHeat heat = new CrucibleHeat();
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 1).with(GooTypes.UNSTABLE, 50);
            assertEquals(UNSTABLE, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            assertEquals(0, stock.volume(GooTypes.BLAZE));
            assertEquals(48, stock.volume(GooTypes.UNSTABLE));
        }

        /** Heat bought with blaze alone waits while the combo burns. */
        @Test
        void boughtHeatWaitsForTheCombo() {
            CrucibleHeat heat = new CrucibleHeat();
            heat.set(3, BLAZE);
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 10).with(GooTypes.UNSTABLE, 10);
            assertEquals(UNSTABLE, heat.burnMeltTick(true, BURN_ORDER, DRAIN, stock));
            assertEquals(3, heat.heatTicks());
        }

        /** A tick with nothing to melt burns no combo fuel. */
        @Test
        void idleTickBurnsNoCombo() {
            MapStock stock = new MapStock().with(GooTypes.BLAZE, 10).with(GooTypes.UNSTABLE, 10);
            assertNull(new CrucibleHeat().burnMeltTick(false, BURN_ORDER, DRAIN, stock));
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
            assertEquals(0.75, GooConfig.BLAZE_MELT_EXPONENT.getDefault());
            assertEquals(1, GooConfig.UNSTABLE_TICKS_PER_MB.getDefault());
            assertEquals(0.5, GooConfig.UNSTABLE_MELT_EXPONENT.getDefault());
            assertEquals(2, GooConfig.COMBO_DRAIN_PER_TICK.getDefault());
        }
    }
}
