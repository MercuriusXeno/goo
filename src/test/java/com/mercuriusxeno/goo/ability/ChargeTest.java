package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A knife-forming charge forms one knife per the ticks its block names up to
 * the knives it names, refilling at that rate after a throw, and Knives ships
 * held at one knife per 3 ticks up to 10 (decisions
 * held-badge-marks-abilities-that-prepare-and-throw, knives-charge-one-per-three-ticks-up-to-ten,
 * knives-refill-from-the-stage-the-fan-rounds-up-to); Blast's shipped charge
 * opens at 2 blocks and gains a block every 12 ticks of hold up to 9
 * (decisions blast-charges-from-two-to-seven-blocks, blast-eases-between-stages-at-a-second-each).
 */
class ChargeTest {

    private static final double DELTA = 1e-9;

    private final AbilityDefinition knivesAbility = AbilityJson.decode("crystal_knives");
    private final Charge knives = knivesAbility.delivery().charge();
    private final Charge blast = AbilityJson.decode("unstable_blast").delivery().charge();

    private static Charge chargeOf(String block) {
        return Charge.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(block)).getOrThrow();
    }

    @Nested
    class KnivesRefillAtTheRateTheBlockNames {

        private final Charge threeTicksToTen = chargeOf("{\"max_ticks\": 30, \"stack\": 10, \"stack_ticks\": 3}");
        private final Charge fiveTicksToFour = chargeOf("{\"max_ticks\": 20, \"stack\": 4, \"stack_ticks\": 5}");

        @Test
        void sixHeldRefillToTenTwelveTicksLater() {
            assertEquals(10, threeTicksToTen.stackedSinceThrow(6, 12));
            assertEquals(9, threeTicksToTen.stackedSinceThrow(6, 11));
        }

        @Test
        void aRefillHoldsAtTheCap() {
            assertEquals(10, threeTicksToTen.stackedSinceThrow(6, 600));
        }

        @Test
        void anotherBlockRefillsByItsOwnNumbers() {
            assertEquals(1, fiveTicksToFour.stackedSinceThrow(1, 4));
            assertEquals(3, fiveTicksToFour.stackedSinceThrow(1, 10));
            assertEquals(4, fiveTicksToFour.stackedSinceThrow(1, 600));
            assertEquals(2, fiveTicksToFour.stackedAt(10));
        }
    }

    @Test
    void shippedKnivesWearsHeldAndFormsOnePerThreeTicksUpToTen() {
        assertEquals(AbilityBadge.HELD, knivesAbility.badge());
        assertEquals(0, knives.stackedAt(2));
        assertEquals(1, knives.stackedAt(3));
        assertEquals(10, knives.stackedAt(30));
        assertEquals(10, knives.stackedAt(60));
    }

    @Test
    void onlyAChargeNamingAStackStacks() {
        assertTrue(knives.stacks());
        assertFalse(Charge.of(30).stacks());
        assertEquals(Charge.NONE, Charge.of(0));
    }

    @ParameterizedTest
    @CsvSource({"0, 2", "6, 2", "11, 2", "12, 3", "24, 4", "36, 5", "48, 6", "60, 7", "72, 8", "83, 8", "84, 9",
        "140, 9", "1000, 9"})
    void blastsChargeGainsABlockEveryTwelveTicksFromTwoToNine(int heldTicks, double stage) {
        assertEquals(stage, blast.stageAt(heldTicks), DELTA);
    }
}
