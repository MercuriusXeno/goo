package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.block.ValuedStack;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The crucible's insert gate read through a mocked {@link IGooValueLookup}, the fake that
 * keeps the lookup an interface (decision delete-dead-fold-mirrors).
 */
class CrucibleInsertionTest {

    private static final Identifier STONE = Identifier.fromNamespaceAndPath("minecraft", "stone");

    private final IGooValueLookup lookup = mock(IGooValueLookup.class);

    @Test
    void itemWithGooValueIsInsertable() {
        when(lookup.lookup(STONE)).thenReturn(new GooValue(Map.of(GooTypes.ROCK, 16)));

        assertTrue(CrucibleInsertion.canInsertItem(STONE, lookup));
        verify(lookup).lookup(STONE);
    }

    /**
     * Melting credits each item's exact value through the crucible's ledger.
     * decision melt-and-fabricate-move-the-exact-value-through-a-remainder-buffer
     */
    @Nested
    class ExactMelt {

        private static final Identifier CARPET = Identifier.withDefaultNamespace("white_carpet");
        private static final Identifier PEBBLE = Identifier.fromNamespaceAndPath("goo", "pebble");

        private final RemainderLedger ledger = new RemainderLedger();

        @Test
        void oneItemWorthSixtyFourThirdsPaysTwentyOneAndKeepsAThird() {
            when(lookup.lookup(CARPET)).thenReturn(fraction(GooTypes.LEAF, 64, 3));

            CrucibleInsertion.MeltArrival arrival = CrucibleInsertion.meltArrival(GooContents.EMPTY, CARPET, 1, lookup, ledger);

            assertEquals(21, arrival.arriving().getVolume(GooTypes.LEAF));
            assertEquals(GooFraction.of(1, 3), ledger.remainders().of(GooTypes.LEAF));
        }

        @Test
        void threeItemsWorthSixtyFourThirdsPaySixtyFourAndKeepNothing() {
            when(lookup.lookup(CARPET)).thenReturn(fraction(GooTypes.LEAF, 64, 3));

            CrucibleInsertion.MeltArrival arrival = CrucibleInsertion.meltArrival(GooContents.EMPTY, CARPET, 3, lookup, ledger);

            assertEquals(64, arrival.arriving().getVolume(GooTypes.LEAF));
            assertTrue(ledger.remainders().isEmpty());
        }

        @Test
        void anItemWorthAThirdPaysOneOnTheThird() {
            when(lookup.lookup(PEBBLE)).thenReturn(fraction(GooTypes.ROCK, 1, 3));

            int[] paid = new int[3];
            for (int i = 0; i < paid.length; i++) {
                paid[i] = CrucibleInsertion.meltArrival(GooContents.EMPTY, PEBBLE, 1, lookup, ledger)
                        .arriving().getVolume(GooTypes.ROCK);
            }

            assertArrayEquals(new int[] {0, 0, 1}, paid);
        }

        @Test
        void theQueuedStacksCarryEveryMbThatArrives() {
            when(lookup.lookup(CARPET)).thenReturn(fraction(GooTypes.LEAF, 64, 3));

            CrucibleInsertion.MeltArrival arrival = CrucibleInsertion.meltArrival(GooContents.EMPTY, CARPET, 3, lookup, ledger);

            int queued = arrival.stacks().stream().mapToInt(stack -> stack.count() * stack.unit().getVolume(GooTypes.LEAF)).sum();
            int items = arrival.stacks().stream().mapToInt(ValuedStack::count).sum();
            assertEquals(64, queued);
            assertEquals(3, items);
        }
    }

    @Test
    void itemWithoutGooValueIsRefused() {
        when(lookup.lookup(STONE)).thenReturn(null);

        assertFalse(CrucibleInsertion.canInsertItem(STONE, lookup));
    }

    @Test
    void itemWithEmptyGooValueIsRefused() {
        when(lookup.lookup(STONE)).thenReturn(GooValue.EMPTY);

        assertFalse(CrucibleInsertion.canInsertItem(STONE, lookup));
    }
}
