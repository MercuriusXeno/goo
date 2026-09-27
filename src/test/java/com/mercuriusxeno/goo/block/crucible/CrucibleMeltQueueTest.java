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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The crucible's melt queue: the head item melts on its own clock while the rest wait,
 * the container walk queues each valued stack in order, and the queue survives a save
 * (decisions pool-keeps-stacks-in-order, melt-time-is-mb-to-a-power).
 */
class CrucibleMeltQueueTest {

    private static final Identifier DIAMOND = Identifier.fromNamespaceAndPath("minecraft", "diamond");
    private static final Identifier OAK_LOG = Identifier.fromNamespaceAndPath("minecraft", "oak_log");
    private static final Identifier STONE = Identifier.fromNamespaceAndPath("minecraft", "stone");
    private static final Identifier STICK = Identifier.fromNamespaceAndPath("minecraft", "stick");
    /** A clock of ceil(mB ^ 0.5) ticks: a 100 mB item melts in 10. */
    private static final double SQUARE_ROOT = 0.5;
    private static final float EPSILON = 1e-6f;
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
     * A queue of a 100 mB diamond ahead of two 16 mB logs, and the pool they brought.
     */
    private static final class Crucible {
        private final CrucibleMeltQueue queue = new CrucibleMeltQueue();
        private final Reservoir reservoir = new Reservoir();
        private GooContents pool = DIAMOND_UNIT.mergeWith(GooContents.EMPTY.withAdded(GooTypes.LEAF, 32));

        Crucible() {
            queue.appendAll(List.of(new ValuedStack(DIAMOND, 1, DIAMOND_UNIT), new ValuedStack(OAK_LOG, 2, LOG_UNIT)));
        }

        void tick(int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                pool = queue.advanceHead(SQUARE_ROOT, pool, reservoir);
            }
        }
    }

    @Nested
    class Clock {

        /** One tick of the diamond's 10 moves a tenth of its crystal; the logs wait whole. */
        @Test
        void headItemAdvancesWhileTheRestWait() {
            Crucible crucible = new Crucible();
            crucible.tick(1);

            CrucibleMeltQueue.Entry head = crucible.queue.head();
            assertNotNull(head);
            assertEquals(DIAMOND, head.item());
            assertEquals(0.1f, head.dissolveFraction(), EPSILON);
            assertEquals(10, crucible.reservoir.volume(GooTypes.CRYSTAL));
            assertEquals(90, crucible.pool.getVolume(GooTypes.CRYSTAL));
            assertEquals(List.of(new CrucibleMeltQueue.Entry(OAK_LOG, 2, LOG_UNIT, 0)), crucible.queue.waiting());
        }

        /** The diamond's tenth tick moves the last of it and hands the head to the logs. */
        @Test
        void finishedItemLeavesTheQueue() {
            Crucible crucible = new Crucible();
            crucible.tick(9);
            assertEquals(DIAMOND, crucible.queue.head().item());
            crucible.tick(1);

            assertEquals(100, crucible.reservoir.volume(GooTypes.CRYSTAL));
            assertEquals(new CrucibleMeltQueue.Entry(OAK_LOG, 2, LOG_UNIT, 0), crucible.queue.head());
        }

        /** A 16 mB log melts in 4 ticks, leaving the next log of its stack whole at the head. */
        @Test
        void finishedItemLeavesItsStacksCount() {
            Crucible crucible = new Crucible();
            crucible.tick(10 + 4);

            assertEquals(16, crucible.reservoir.volume(GooTypes.LEAF));
            assertEquals(new CrucibleMeltQueue.Entry(OAK_LOG, 1, LOG_UNIT, 0), crucible.queue.head());
        }

        /** Melting every item empties the queue and the pool together. */
        @Test
        void lastItemFinishingEmptiesTheQueue() {
            Crucible crucible = new Crucible();
            crucible.tick(10 + 4 + 4);

            assertTrue(crucible.queue.isEmpty());
            assertNull(crucible.queue.head());
            assertTrue(crucible.pool.isEmpty());
        }

        /** A 100 mB item of 60 rock and 40 metal moves each of its types in proportion. */
        @Test
        void itemMovesEachOfItsTypesInProportion() {
            GooContents stone = new GooContents(Map.of(GooTypes.ROCK, 60, GooTypes.METAL, 40));
            CrucibleMeltQueue queue = new CrucibleMeltQueue();
            queue.appendAll(List.of(new ValuedStack(STONE, 1, stone)));
            Reservoir reservoir = new Reservoir();
            GooContents pool = stone;
            for (int tick = 0; tick < 5; tick++) {
                pool = queue.advanceHead(SQUARE_ROOT, pool, reservoir);
            }

            assertEquals(30, reservoir.volume(GooTypes.ROCK));
            assertEquals(20, reservoir.volume(GooTypes.METAL));
        }

        /** A reservoir refusing one of the item's types holds the whole tick: nothing moves, the clock stands. */
        @Test
        void refusedShareHoldsTheClock() {
            GooContents stone = new GooContents(Map.of(GooTypes.ROCK, 60, GooTypes.METAL, 40));
            CrucibleMeltQueue queue = new CrucibleMeltQueue();
            queue.appendAll(List.of(new ValuedStack(STONE, 1, stone)));
            Reservoir reservoir = new Reservoir();
            reservoir.refused.add(GooTypes.METAL);

            assertEquals(stone, queue.advanceHead(SQUARE_ROOT, stone, reservoir));
            assertEquals(0, reservoir.volume(GooTypes.ROCK));
            assertEquals(0f, queue.head().dissolveFraction(), EPSILON);
        }

        /** An item's share never takes more than the pool holds of a type. */
        @Test
        void shareNeverExceedsThePool() {
            CrucibleMeltQueue queue = new CrucibleMeltQueue();
            queue.appendAll(List.of(new ValuedStack(DIAMOND, 1, DIAMOND_UNIT)));
            Reservoir reservoir = new Reservoir();

            GooContents after = queue.advanceHead(SQUARE_ROOT, GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, 4), reservoir);

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

        /** Three valued stacks and a valueless one in a container queue three entries in walk order. */
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
                    new CrucibleMeltQueue.Entry(OAK_LOG, 3, GooContents.EMPTY.withAdded(GooTypes.LEAF, 8), 0),
                    new CrucibleMeltQueue.Entry(DIAMOND, 1, GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, 50), 0),
                    new CrucibleMeltQueue.Entry(STONE, 2, stone.toGooContents(), 0)), queue.entries());
        }
    }

    @Nested
    class Saving {

        /** A part-melted queue saves and loads with its order, items and progress intact. */
        @Test
        void queueRoundTripsThroughItsCodec() {
            Crucible crucible = new Crucible();
            crucible.tick(3);

            JsonElement saved = CrucibleMeltQueue.CODEC.encodeStart(JsonOps.INSTANCE, crucible.queue.entries())
                    .getOrThrow();
            CrucibleMeltQueue loaded = new CrucibleMeltQueue();
            loaded.loadFrom(CrucibleMeltQueue.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());

            assertEquals(crucible.queue.entries(), loaded.entries());
            assertEquals(0.3f, loaded.head().dissolveFraction(), EPSILON);
        }
    }
}
