package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GooDeposit walks carried slots in the order canister, goo, vat, new goo, and
 * answers what found no home (decision drained-goo-fills-carried-containers-first).
 * Slots are fakes, so no ItemStack or registry is touched.
 */
class GooDepositTest {

    private static final ResourceKey<GooTypeDefinition> ROCK = GooTypes.ROCK;
    private static final ResourceKey<GooTypeDefinition> METAL = GooTypes.METAL;

    @Nested
    class WalkOrder {

        @Test
        void canisterFillsBeforeGoo() {
            FakeDepositSlot goo = FakeDepositSlot.goo(ROCK, 500);
            FakeDepositSlot canister = FakeDepositSlot.canister(ROCK, 700, 1_000);

            int left = GooDeposit.depositInto(List.of(goo, canister), ROCK, 800);

            assertAll(() -> assertEquals(0, left),
                    () -> assertEquals(1_000, canister.volume(ROCK)),
                    () -> assertEquals(1_000, goo.volume(ROCK)));
        }

        @Test
        void otherTypeCanisterAndGooAreSkipped() {
            FakeDepositSlot canister = FakeDepositSlot.canister(METAL, 100, 1_000);
            FakeDepositSlot goo = FakeDepositSlot.goo(METAL, 100);
            FakeDepositSlot vat = FakeDepositSlot.vat(10_000);

            GooDeposit.depositInto(List.of(canister, goo, vat), ROCK, 400);

            assertAll(() -> assertEquals(0, canister.volume(ROCK)),
                    () -> assertEquals(0, goo.volume(ROCK)),
                    () -> assertEquals(400, vat.volume(ROCK)));
        }

        @Test
        void vatTakesGooBeforeAFreeSlot() {
            FakeDepositSlot free = FakeDepositSlot.empty();
            FakeDepositSlot vat = FakeDepositSlot.vat(10_000);

            int left = GooDeposit.depositInto(List.of(free, vat), ROCK, 3_000);

            assertAll(() -> assertEquals(0, left),
                    () -> assertEquals(3_000, vat.volume(ROCK)),
                    () -> assertEquals(0, free.total()));
        }

        @Test
        void freeSlotGetsANewGooOfTheVolume() {
            FakeDepositSlot unrelated = FakeDepositSlot.unrelated();
            FakeDepositSlot free = FakeDepositSlot.empty();

            int left = GooDeposit.depositInto(List.of(unrelated, free), ROCK, 2_500);

            assertAll(() -> assertEquals(0, left),
                    () -> assertEquals(2_500, free.volume(ROCK)),
                    () -> assertEquals(GooDeposit.Home.GOO, free.home()));
        }

        @Test
        void fullVatOverflowsIntoAFreeSlot() {
            FakeDepositSlot vat = FakeDepositSlot.vat(1_000);
            FakeDepositSlot free = FakeDepositSlot.empty();

            GooDeposit.depositInto(List.of(vat, free), ROCK, 1_500);

            assertAll(() -> assertEquals(1_000, vat.volume(ROCK)),
                    () -> assertEquals(500, free.volume(ROCK)));
        }
    }

    @Nested
    class NoHome {

        @Test
        void unrelatedSlotsAnswerTheWholeVolume() {
            List<FakeDepositSlot> slots = List.of(FakeDepositSlot.unrelated(), FakeDepositSlot.unrelated());

            assertEquals(4_000, GooDeposit.depositInto(slots, ROCK, 4_000));
        }

        @Test
        void drainDrawsOnlyWhatFoundAHome() {
            Map<ResourceKey<GooTypeDefinition>, Integer> container = new LinkedHashMap<>();
            container.put(ROCK, 1_500);
            container.put(METAL, 700);
            FakeDepositSlot vat = FakeDepositSlot.vat(1_000);
            Map<ResourceKey<GooTypeDefinition>, Integer> drawn = new HashMap<>();

            boolean moved = GooDeposit.drainEveryType(container,
                    (type, volume) -> { drawn.merge(type, volume, Integer::sum); return volume; },
                    (type, volume) -> GooDeposit.depositInto(List.of(vat), type, volume));

            assertAll(() -> assertTrue(moved),
                    () -> assertEquals(Map.of(ROCK, 1_000), drawn),
                    () -> assertEquals(0, vat.volume(METAL)));
        }

        @Test
        void drainWithNoHomeMovesNothing() {
            Map<ResourceKey<GooTypeDefinition>, Integer> drawn = new HashMap<>();

            boolean moved = GooDeposit.drainEveryType(Map.of(ROCK, 900),
                    (type, volume) -> { drawn.merge(type, volume, Integer::sum); return volume; },
                    (type, volume) -> volume);

            assertAll(() -> assertFalse(moved), () -> assertTrue(drawn.isEmpty()));
        }
    }

    @Test
    void gooGrowthStopsShortOfOverflow() {
        assertAll(() -> assertEquals(10, GooDeposit.gooRoom(Integer.MAX_VALUE - 10, 50)),
                () -> assertEquals(50, GooDeposit.gooRoom(100, 50)));
    }
}
