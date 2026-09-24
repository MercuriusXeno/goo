package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for the hub item's canister routing over fake canisters, since gooTest
 * cannot bootstrap an ItemStack (decision hub-item-blob-insert).
 */
class HubCanisterRoutingTest {

    private static final int CAPACITY = 16_000;
    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final ResourceKey<GooTypeDefinition> NETHER = GooTypes.NETHER;

    /**
     * An empty canister takes the whole offer.
     */
    @Test
    void route_emptyCanister_receivesVolume() {
        FakeCanister empty = FakeCanister.empty();

        int accepted = HubCanisterRouting.route(List.of(empty), ROCK, 3_000);

        assertEquals(3_000, accepted);
        assertEquals(ROCK, empty.type);
        assertEquals(3_000, empty.amount);
    }

    /**
     * A same-type canister later in the list fills before an earlier empty one.
     */
    @Test
    void route_matchingCanister_fillsBeforeEmpty() {
        FakeCanister empty = FakeCanister.empty();
        FakeCanister matching = FakeCanister.holding(ROCK, 1_000);

        HubCanisterRouting.route(List.of(empty, matching), ROCK, 2_000);

        assertEquals(3_000, matching.amount);
        assertEquals(0, empty.amount);
        assertNull(empty.type);
    }

    /**
     * A matching canister at capacity spills the remainder into the next eligible canister.
     */
    @Test
    void route_fullMatchingCanister_spillsRemainder() {
        FakeCanister nearlyFull = FakeCanister.holding(ROCK, CAPACITY - 1_000);
        FakeCanister empty = FakeCanister.empty();

        int accepted = HubCanisterRouting.route(List.of(nearlyFull, empty), ROCK, 4_000);

        assertEquals(4_000, accepted);
        assertEquals(CAPACITY, nearlyFull.amount);
        assertEquals(3_000, empty.amount);
    }

    /**
     * An offer larger than the free room is capped, answering what fit.
     */
    @Test
    void route_offerExceedsRoom_acceptsCapped() {
        FakeCanister empty = FakeCanister.empty();

        int accepted = HubCanisterRouting.route(List.of(empty), ROCK, CAPACITY + 5_000);

        assertEquals(CAPACITY, accepted);
        assertEquals(CAPACITY, empty.amount);
    }

    /**
     * No canisters: nothing accepted.
     */
    @Test
    void route_noCanisters_acceptsNothing() {
        assertEquals(0, HubCanisterRouting.route(List.of(), ROCK, 1_000));
    }

    /**
     * A full matching canister and a mismatched one: nothing accepted, both unchanged.
     */
    @Test
    void route_fullAndMismatched_acceptsNothingAndChangesNothing() {
        FakeCanister full = FakeCanister.holding(ROCK, CAPACITY);
        FakeCanister mismatched = FakeCanister.holding(NETHER, 2_000);

        int accepted = HubCanisterRouting.route(List.of(full, mismatched), ROCK, 1_000);

        assertEquals(0, accepted);
        assertEquals(CAPACITY, full.amount);
        assertEquals(ROCK, full.type);
        assertEquals(2_000, mismatched.amount);
        assertEquals(NETHER, mismatched.type);
    }

    /**
     * A single-type canister with a fixed capacity, standing in for a canister item stack.
     */
    private static final class FakeCanister implements HubCanisterRouting.RoutableCanister {
        private @Nullable ResourceKey<GooTypeDefinition> type;
        private int amount;

        private FakeCanister(@Nullable ResourceKey<GooTypeDefinition> type, int amount) {
            this.type = type;
            this.amount = amount;
        }

        static FakeCanister empty() {
            return new FakeCanister(null, 0);
        }

        static FakeCanister holding(ResourceKey<GooTypeDefinition> type, int amount) {
            return new FakeCanister(type, amount);
        }

        @Override
        public boolean isEmpty() {
            return amount <= 0;
        }

        @Override
        public boolean holds(ResourceKey<GooTypeDefinition> offered) {
            return !isEmpty() && offered.equals(type);
        }

        @Override
        public int addGoo(ResourceKey<GooTypeDefinition> offered, int volume) {
            if (!isEmpty() && !offered.equals(type)) { return 0; }
            int taken = Math.min(volume, CAPACITY - amount);
            if (taken > 0) {
                type = offered;
                amount += taken;
            }
            return taken;
        }
    }
}
