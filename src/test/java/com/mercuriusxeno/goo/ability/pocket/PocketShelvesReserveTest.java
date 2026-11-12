package com.mercuriusxeno.goo.ability.pocket;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the void a shelf stored under the old Pocket reserved, paid back once: six for each slot its
 * entries fill on the first settle, nothing after.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
class PocketShelvesReserveTest {

    private static final int STACK = 64;
    private static final int OLD_SLOT_COST = 6;
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static PocketShelves seeded() {
        ItemStack stone = mock(ItemStack.class);
        when(stone.getMaxStackSize()).thenReturn(STACK);
        ItemStack sword = mock(ItemStack.class);
        when(sword.getMaxStackSize()).thenReturn(1);
        return new PocketShelves(Map.of(OWNER, List.of(new PocketEntry(stone, 200), new PocketEntry(sword, 2))));
    }

    @Test
    void firstSettlePaysSixForEachSlotTheShelfFills() {
        assertEquals((4 + 2) * OLD_SLOT_COST, seeded().settleReserve(OWNER));
    }

    @Test
    void secondSettlePaysNothing() {
        PocketShelves shelves = seeded();
        shelves.settleReserve(OWNER);
        assertEquals(0, shelves.settleReserve(OWNER));
    }

    @Test
    void aPlayerWithNoShelfIsOwedNothing() {
        assertEquals(0, seeded().settleReserve(UUID.fromString("00000000-0000-0000-0000-000000000002")));
    }
}
