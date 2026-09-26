package com.mercuriusxeno.goo.block.crucible;

import com.google.gson.JsonElement;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.ContainerEvaluator;
import com.mercuriusxeno.goo.block.ValuedStack;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The crucible's melt queue: stacks drain oldest first, the container walk queues each
 * valued stack in order, and the queue survives a save (decision pool-keeps-stacks-in-order).
 */
class CrucibleMeltQueueTest {

    private static final Identifier DIAMOND = Identifier.fromNamespaceAndPath("minecraft", "diamond");
    private static final Identifier OAK_LOG = Identifier.fromNamespaceAndPath("minecraft", "oak_log");
    private static final Identifier STONE = Identifier.fromNamespaceAndPath("minecraft", "stone");
    private static final Identifier STICK = Identifier.fromNamespaceAndPath("minecraft", "stick");
    private static final float EPSILON = 1e-6f;

    /**
     * Builds a queue holding a 100 mB diamond ahead of a 40 mB log.
     *
     * @return the queue
     */
    private static CrucibleMeltQueue diamondThenLog() {
        CrucibleMeltQueue queue = new CrucibleMeltQueue();
        queue.appendAll(List.of(new ValuedStack(DIAMOND, 1, 100), new ValuedStack(OAK_LOG, 2, 40)));
        return queue;
    }

    @Nested
    class Draining {

        /** A 25 mB drain charges the diamond alone, a quarter dissolved, the log still waiting whole. */
        @Test
        void drainChargesTheOldestStack() {
            CrucibleMeltQueue queue = diamondThenLog();
            queue.charge(25);

            CrucibleMeltQueue.Entry head = queue.head();
            assertNotNull(head);
            assertEquals(DIAMOND, head.item());
            assertEquals(0.25f, head.dissolveFraction(), EPSILON);
            assertEquals(List.of(new CrucibleMeltQueue.Entry(OAK_LOG, 2, 40, 0)), queue.waiting());
        }

        /** The head's dissolve fraction rises with each drain against it. */
        @Test
        void dissolveFractionRisesWithDrain() {
            CrucibleMeltQueue queue = diamondThenLog();
            queue.charge(10);
            float first = queue.head().dissolveFraction();
            queue.charge(50);

            assertEquals(0.1f, first, EPSILON);
            assertEquals(0.6f, queue.head().dissolveFraction(), EPSILON);
        }

        /** A 110 mB drain removes the 100 mB diamond and charges the 10 mB overflow to the log. */
        @Test
        void drainPastTheHeadChargesTheNext() {
            CrucibleMeltQueue queue = diamondThenLog();
            queue.charge(110);

            assertEquals(new CrucibleMeltQueue.Entry(OAK_LOG, 2, 40, 10), queue.head());
            assertTrue(queue.waiting().isEmpty());
        }

        /** Draining every mB the stacks brought leaves an empty queue, as the emptied pool does. */
        @Test
        void emptiedPoolLeavesEmptyQueue() {
            CrucibleMeltQueue queue = diamondThenLog();
            queue.charge(60);
            queue.charge(80);

            assertTrue(queue.isEmpty());
            assertNull(queue.head());
        }

        /** A stack carrying no goo joins no queue. */
        @Test
        void stackWithoutGooIsNotQueued() {
            CrucibleMeltQueue queue = new CrucibleMeltQueue();
            queue.appendAll(List.of(new ValuedStack(STICK, 1, 0)));

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
            when(lookup.lookup(DIAMOND)).thenReturn(new GooValue(Map.of(GooTypes.CRYSTAL, 50)));
            when(lookup.lookup(OAK_LOG)).thenReturn(new GooValue(Map.of(GooTypes.LEAF, 8)));
            when(lookup.lookup(STONE)).thenReturn(new GooValue(Map.of(GooTypes.ROCK, 4, GooTypes.METAL, 2)));
            CrucibleMeltQueue queue = new CrucibleMeltQueue();

            walkInto(queue, List.of(Map.entry(OAK_LOG, 3), Map.entry(STICK, 5),
                    Map.entry(DIAMOND, 1), Map.entry(STONE, 2)));

            assertEquals(List.of(
                    new CrucibleMeltQueue.Entry(OAK_LOG, 3, 24, 0),
                    new CrucibleMeltQueue.Entry(DIAMOND, 1, 50, 0),
                    new CrucibleMeltQueue.Entry(STONE, 2, 12, 0)), queue.entries());
        }
    }

    @Nested
    class Saving {

        /** A part-drained queue saves and loads with its order and volumes intact. */
        @Test
        void queueRoundTripsThroughItsCodec() {
            CrucibleMeltQueue queue = diamondThenLog();
            queue.charge(30);

            JsonElement saved = CrucibleMeltQueue.CODEC.encodeStart(JsonOps.INSTANCE, queue.entries()).getOrThrow();
            CrucibleMeltQueue loaded = new CrucibleMeltQueue();
            loaded.loadFrom(CrucibleMeltQueue.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());

            assertEquals(List.of(
                    new CrucibleMeltQueue.Entry(DIAMOND, 1, 100, 30),
                    new CrucibleMeltQueue.Entry(OAK_LOG, 2, 40, 0)), loaded.entries());
        }
    }
}
