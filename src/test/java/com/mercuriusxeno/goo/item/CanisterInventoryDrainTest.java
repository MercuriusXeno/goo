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
 * the old 64,000 mB blob cap, every type a vat holds included, and keeps what finds no home
 * (decisions vats-and-canisters-drain-whole and vat-click-unpacks-into-inventory).
 * The drain takes no SlotAccess, so it has no cursor to set; the inventory is fake deposit slots.
 */
class CanisterInventoryDrainTest {

    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final ResourceKey<GooTypeDefinition> NETHER = GooTypes.NETHER;
    private static final int HELD = 200_000;
    private static final int NETHER_HELD = 90_000;

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

    @Test
    void twoTypeVatUnpacksIntoOneOmniblobPerType() {
        FakeSource vat = new FakeSource(ROCK, HELD);
        vat.add(NETHER, NETHER_HELD);
        FakeDepositSlot first = FakeDepositSlot.empty();
        FakeDepositSlot second = FakeDepositSlot.empty();

        boolean moved = CanisterInventoryHandler.drainIntoInventory(vat, depositorOver(List.of(first, second)));

        assertAll(() -> assertTrue(moved),
                () -> assertEquals(HELD, first.volume(ROCK) + second.volume(ROCK)),
                () -> assertEquals(NETHER_HELD, first.volume(NETHER) + second.volume(NETHER)),
                () -> assertTrue(first.volume(ROCK) == 0 || first.volume(NETHER) == 0),
                () -> assertEquals(0, vat.volume(ROCK) + vat.volume(NETHER)));
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

        void add(ResourceKey<GooTypeDefinition> type, int volume) {
            held.merge(type, volume, Integer::sum);
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
