package com.mercuriusxeno.goo.block.crucible;

import com.google.gson.JsonElement;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.ContainerEvaluator;
import com.mercuriusxeno.goo.block.ValuedStack;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The crucible's melt queue: each item melts on its own clock, a lone fuel's cursor
 * advancing one item per tick in turn, the container walk queues each valued stack in
 * order, and the queue survives a save (decisions pool-keeps-stacks-in-order,
 * melt-time-is-mb-to-a-power, lone-fuel-advances-one-item).
 */
class CrucibleMeltQueueTest {

    private static final Identifier DIAMOND = Identifier.fromNamespaceAndPath("minecraft", "diamond");
    private static final Identifier OAK_LOG = Identifier.fromNamespaceAndPath("minecraft", "oak_log");
    private static final Identifier STONE = Identifier.fromNamespaceAndPath("minecraft", "stone");
    private static final Identifier STICK = Identifier.fromNamespaceAndPath("minecraft", "stick");
    /** A clock of ceil(mB ^ 0.5) ticks: a 100 mB item melts in 10, a 16 mB item in 4. */
    private static final double SQUARE_ROOT = 0.5;
    private static final double EPSILON = 1e-9;
    private static final GooContents DIAMOND_UNIT = GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, 100);
    private static final GooContents LOG_UNIT = GooContents.EMPTY.withAdded(GooTypes.LEAF, 16);

    /** A map-backed reservoir that refuses the types it is told to. */
    private static final class Reservoir implements CrucibleMeltQueue.MeltSink {
        private final Map<ResourceKey<GooTypeDefinition>, Integer> held = new HashMap<>();
        private final List<ResourceKey<GooTypeDefinition>> refused = new ArrayList<>();

        @Override
        public int accept(ResourceKey<GooTypeDefinition> type, int amount, boolean simulate) {
            if (refused.contains(type)) {
                return 0;
            }
            if (!simulate) {
                held.merge(type, amount, Integer::sum);
            }
            return amount;
        }

        int volume(ResourceKey<GooTypeDefinition> type) {
            return held.getOrDefault(type, 0);
        }
    }

    /**
     * A queue of stacks, the pool they brought and the reservoir they melt into.
     */
    private static final class Crucible {
        private final CrucibleMeltQueue queue = new CrucibleMeltQueue();
        private final Reservoir reservoir = new Reservoir();
        private GooContents pool = GooContents.EMPTY;

        /** A 100 mB diamond ahead of two 16 mB logs. */
        Crucible() {
            this(new ValuedStack(DIAMOND, 1, DIAMOND_UNIT), new ValuedStack(OAK_LOG, 2, LOG_UNIT));
        }

        Crucible(ValuedStack... stacks) {
            for (ValuedStack stack : stacks) {
                queue.appendAll(List.of(stack));
                stack.unit().getAll().forEach((type, volume) -> pool = pool.withAdded(type, volume * stack.count()));
            }
        }

        void tick(int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                pool = queue.advanceNext(SQUARE_ROOT, pool, reservoir);
            }
        }

        List<Double> progressOf(int entry) {
            return queue.entries().get(entry).progress();
        }
    }

    /**
     * Asserts each progress equals its expected value within floating error.
     *
     * @param expected the expected progress, oldest item first
     * @param actual   the actual progress
     */
    private static void assertProgress(List<Double> expected, List<Double> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), EPSILON);
        }
    }

    @Nested
    class Clock {

        /** One tick moves a tenth of the diamond's crystal and leaves both logs whole. */
        @Test
        void oneTickAdvancesOnlyTheItemUnderTheCursor() {
            Crucible crucible = new Crucible();
            crucible.tick(1);

            assertProgress(List.of(0.1), crucible.progressOf(0));
            assertProgress(List.of(0.0, 0.0), crucible.progressOf(1));
            assertEquals(10, crucible.reservoir.volume(GooTypes.CRYSTAL));
            assertEquals(90, crucible.pool.getVolume(GooTypes.CRYSTAL));
        }

        /** Three ticks advance the diamond and then each log of the stack once, in turn. */
        @Test
        void cursorVisitsEachItemOfAStackInTurn() {
            Crucible crucible = new Crucible();
            crucible.tick(3);

            assertProgress(List.of(0.1), crucible.progressOf(0));
            assertProgress(List.of(0.25, 0.25), crucible.progressOf(1));
        }

        /** The fourth tick wraps the cursor back to the diamond. */
        @Test
        void cursorWrapsToTheFirstItem() {
            Crucible crucible = new Crucible();
            crucible.tick(4);

            assertProgress(List.of(0.2), crucible.progressOf(0));
            assertProgress(List.of(0.25, 0.25), crucible.progressOf(1));
        }

        /** The first log's fourth turn finishes it on tick 11, and the second log's finishes it on tick 12. */
        @Test
        void finishedItemLeavesItsStackAndTheCursorTakesTheNext() {
            Crucible crucible = new Crucible();
            crucible.tick(11);
            assertProgress(List.of(0.75), crucible.progressOf(1));
            assertEquals(16 + 12, crucible.reservoir.volume(GooTypes.LEAF));

            crucible.tick(1);
            assertEquals(1, crucible.queue.entries().size());
            assertEquals(32, crucible.reservoir.volume(GooTypes.LEAF));
            assertProgress(List.of(0.4), crucible.progressOf(0));
        }

        /** With the logs gone the diamond takes every tick, finishing on tick 18. */
        @Test
        void lastItemFinishingEmptiesTheQueue() {
            Crucible crucible = new Crucible();
            crucible.tick(17);
            assertEquals(DIAMOND, crucible.queue.head().item());
            crucible.tick(1);

            assertTrue(crucible.queue.isEmpty());
            assertNull(crucible.queue.head());
            assertTrue(crucible.pool.isEmpty());
            assertEquals(100, crucible.reservoir.volume(GooTypes.CRYSTAL));
        }

        /** Two 100 mB items of one stack finish on consecutive ticks, 19 and 20, at twice one item's 10. */
        @Test
        void stackOfTwoFinishesOnConsecutiveTicks() {
            Crucible crucible = new Crucible(new ValuedStack(DIAMOND, 2, DIAMOND_UNIT));
            crucible.tick(18);
            assertEquals(2, crucible.queue.head().count());
            crucible.tick(1);
            assertEquals(1, crucible.queue.head().count());
            crucible.tick(1);

            assertTrue(crucible.queue.isEmpty());
            assertEquals(200, crucible.reservoir.volume(GooTypes.CRYSTAL));
        }

        /**
         * The combo advances every item one tick and leaves the cursor on its item, so the
         * lone tick after it takes the first log (decision combo-advances-every-item).
         */
        @Test
        void comboAdvancesEveryItemAndLeavesTheCursor() {
            Crucible crucible = new Crucible();
            crucible.tick(1);
            crucible.pool = crucible.queue.advanceEvery(SQUARE_ROOT, crucible.pool, crucible.reservoir);
            assertProgress(List.of(0.2), crucible.progressOf(0));
            assertProgress(List.of(0.25, 0.25), crucible.progressOf(1));

            crucible.tick(1);
            assertProgress(List.of(0.5, 0.25), crucible.progressOf(1));
        }

        /** Items the combo finishes ahead of the cursor pull it back, so it stays on its item. */
        @Test
        void comboFinishingItemsKeepsTheCursorOnItsItem() {
            Crucible crucible = new Crucible(new ValuedStack(OAK_LOG, 2, LOG_UNIT), new ValuedStack(DIAMOND, 1, DIAMOND_UNIT));
            crucible.tick(2);
            for (int tick = 0; tick < 3; tick++) {
                crucible.pool = crucible.queue.advanceEvery(SQUARE_ROOT, crucible.pool, crucible.reservoir);
            }
            assertEquals(List.of(DIAMOND), crucible.queue.entries().stream().map(CrucibleMeltQueue.Entry::item).toList());
            assertEquals(0, crucible.queue.saved().cursor());
            assertEquals(32, crucible.reservoir.volume(GooTypes.LEAF));
        }

        /** A 100 mB item of 60 rock and 40 metal moves each of its types in proportion. */
        @Test
        void itemMovesEachOfItsTypesInProportion() {
            GooContents stone = new GooContents(Map.of(GooTypes.ROCK, 60, GooTypes.METAL, 40));
            Crucible crucible = new Crucible(new ValuedStack(STONE, 1, stone));
            crucible.tick(5);

            assertEquals(30, crucible.reservoir.volume(GooTypes.ROCK));
            assertEquals(20, crucible.reservoir.volume(GooTypes.METAL));
        }

        /**
         * A reservoir refusing crystal holds the diamond's tick whole, and the cursor moves on
         * to the log, which melts.
         */
        @Test
        void refusedShareHoldsTheItemAndTheCursorMovesOn() {
            Crucible crucible = new Crucible(new ValuedStack(DIAMOND, 1, DIAMOND_UNIT),
                    new ValuedStack(OAK_LOG, 1, LOG_UNIT));
            crucible.reservoir.refused.add(GooTypes.CRYSTAL);
            crucible.tick(2);

            assertEquals(100, crucible.pool.getVolume(GooTypes.CRYSTAL));
            assertProgress(List.of(0.0), crucible.progressOf(0));
            assertProgress(List.of(0.25), crucible.progressOf(1));
        }

        /** An item's share never takes more than the pool holds of a type. */
        @Test
        void shareNeverExceedsThePool() {
            CrucibleMeltQueue queue = new CrucibleMeltQueue();
            queue.appendAll(List.of(new ValuedStack(DIAMOND, 1, DIAMOND_UNIT)));
            Reservoir reservoir = new Reservoir();

            GooContents after = queue.advanceNext(SQUARE_ROOT, GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, 4), reservoir);

            assertEquals(4, reservoir.volume(GooTypes.CRYSTAL));
            assertTrue(after.isEmpty());
        }

        /** An exact power reads its whole ticks: 100 ^ 0.5 is 10, not 11. */
        @Test
        void exactPowerReadsItsWholeTicks() {
            assertEquals(10, CrucibleMath.meltTicks(100, SQUARE_ROOT));
            assertEquals(1, CrucibleMath.meltTicks(1, 0.75));
        }

        /** A stack carrying no goo joins no queue. */
        @Test
        void stackWithoutGooIsNotQueued() {
            CrucibleMeltQueue queue = new CrucibleMeltQueue();
            queue.appendAll(List.of(new ValuedStack(STICK, 1, GooContents.EMPTY)));

            assertTrue(queue.isEmpty());
        }
    }

    @Nested
    class ContainerWalk {

        private final IGooValueLookup lookup = mock(IGooValueLookup.class);

        /**
         * Values the stacks as the walk does, the valueless ones dropped, then queues them.
         *
         * @param queue  the queue the walk's stacks join
         * @param stacks each stack's item id and count, in walk order
         */
        private void walkInto(CrucibleMeltQueue queue, List<Map.Entry<Identifier, Integer>> stacks) {
            List<ValuedStack> valued = new ArrayList<>();
            for (Map.Entry<Identifier, Integer> stack : stacks) {
                ValuedStack priced = ContainerEvaluator.valuedStack(stack.getKey(), stack.getValue(), lookup);
                if (priced != null) {
                    valued.add(priced);
                }
            }
            queue.appendAll(valued);
        }

        /** Three valued stacks and a valueless one in a container queue three entries in walk order, each item whole. */
        @Test
        void containerOfThreeStacksAppendsThreeEntries() {
            GooValue stone = new GooValue(Map.of(GooTypes.ROCK, 4, GooTypes.METAL, 2));
            when(lookup.lookup(DIAMOND)).thenReturn(new GooValue(Map.of(GooTypes.CRYSTAL, 50)));
            when(lookup.lookup(OAK_LOG)).thenReturn(new GooValue(Map.of(GooTypes.LEAF, 8)));
            when(lookup.lookup(STONE)).thenReturn(stone);
            CrucibleMeltQueue queue = new CrucibleMeltQueue();

            walkInto(queue, List.of(Map.entry(OAK_LOG, 3), Map.entry(STICK, 5),
                    Map.entry(DIAMOND, 1), Map.entry(STONE, 2)));

            assertEquals(List.of(
                    new CrucibleMeltQueue.Entry(OAK_LOG, GooContents.EMPTY.withAdded(GooTypes.LEAF, 8),
                            List.of(0.0, 0.0, 0.0)),
                    new CrucibleMeltQueue.Entry(DIAMOND, GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, 50), List.of(0.0)),
                    new CrucibleMeltQueue.Entry(STONE, stone.toGooContents(), List.of(0.0, 0.0))), queue.entries());
        }
    }

    @Nested
    class Saving {

        /** A part-melted queue saves and loads with its items' progress and its cursor intact. */
        @Test
        void queueRoundTripsThroughItsCodec() {
            Crucible crucible = new Crucible();
            crucible.tick(2);

            JsonElement saved = CrucibleMeltQueue.CODEC.encodeStart(JsonOps.INSTANCE, crucible.queue.saved())
                    .getOrThrow();
            CrucibleMeltQueue loaded = new CrucibleMeltQueue();
            loaded.loadFrom(CrucibleMeltQueue.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());

            assertEquals(crucible.queue.saved(), loaded.saved());
            assertEquals(2, loaded.saved().cursor());
        }
    }
}
