package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CanisterInventoryHandler.drainIntoInventory empties a source whole into the inventory, past
 * the old 64,000 mB blob cap, and keeps what finds no home (decision vats-and-canisters-drain-whole).
 * The drain takes no SlotAccess, so it has no cursor to set; the inventory is fake deposit slots.
 */
class CanisterInventoryDrainTest {

    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final int HELD = 200_000;

    @Test
    void canisterDrainsWholeIntoAFreeSlot() {
        FakeSource canister = new FakeSource(ROCK, HELD);
        FakeDepositSlot free = FakeDepositSlot.empty();

        boolean moved = CanisterInventoryHandler.drainIntoInventory(canister, depositorOver(List.of(free)));

        assertAll(() -> assertTrue(moved),
                () -> assertEquals(HELD, free.volume(ROCK)),
                () -> assertEquals(0, canister.volume(ROCK)));
    }

    @Test
    void canisterWithNoHomeKeepsItsGoo() {
        FakeSource canister = new FakeSource(ROCK, HELD);
        List<FakeDepositSlot> full = List.of(FakeDepositSlot.unrelated(), FakeDepositSlot.unrelated());

        boolean moved = CanisterInventoryHandler.drainIntoInventory(canister, depositorOver(full));

        assertAll(() -> assertFalse(moved), () -> assertEquals(HELD, canister.volume(ROCK)));
    }

    private static GooDeposit.Depositor depositorOver(List<FakeDepositSlot> slots) {
        return (type, volume) -> GooDeposit.depositInto(slots, type, volume);
    }

    /** A drain source holding goo in a map. */
    private static final class FakeSource implements CanisterInventoryHandler.GooSource {
        private final Map<ResourceKey<GooTypeDefinition>, Integer> held = new HashMap<>();

        FakeSource(ResourceKey<GooTypeDefinition> type, int volume) {
            held.put(type, volume);
        }

        int volume(ResourceKey<GooTypeDefinition> type) {
            return held.getOrDefault(type, 0);
        }

        @Override
        public Map<ResourceKey<GooTypeDefinition>, Integer> drainable() {
            return Map.copyOf(held);
        }

        @Override
        public int remove(ResourceKey<GooTypeDefinition> type, int volume) {
            int taken = Math.min(volume, volume(type));
            held.put(type, volume(type) - taken);
            return taken;
        }
    }
}
