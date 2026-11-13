package com.mercuriusxeno.goo.ability.outsider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the ticks a void holding sustains an Outsider room's growth: the
 * reckoner's count equals the ticks a hold fed tick by tick pays before the
 * holding falls short, beside the void upkeep standing each tick, at the
 * params void_outsider.json ships
 * (decision outsider-panel-reads-the-seconds-the-void-affords).
 */
class SustainedGrowthTest {

    /** Void enough to carry the laid room through ring 5, 700 mB and its upkeep, and part way into ring 6. */
    private static final int HOLDING = 1500;
    /** The extent once ring 5 is whole, which the holding must carry the room past. */
    private static final double RING_FIVE_WHOLE = 5.5;
    /** The holding that stalled the game for seconds when the reckoner walked it tick by tick. */
    private static final double BILLION = 1.0E9;
    /**
     * Longer than the closed form takes on the busiest test machine, shorter than the seconds a walk of a billion
     * mB took: 50 ms timed out under the whole suite's load though the closed form takes microseconds.
     */
    private static final Duration WALK_WOULD_TAKE_LONGER = Duration.ofSeconds(1);

    private final JsonObject outsider = JsonParser.parseString(AbilityJson.read("void_outsider")).getAsJsonObject()
            .getAsJsonArray("behaviors").get(0).getAsJsonObject();
    private final int ringTicks = outsider.get("ring_ticks").getAsInt();
    private final int base = outsider.get("cell_cost").getAsInt();
    private final int perDistance = outsider.get("distance_cost").getAsInt();

    @ParameterizedTest(name = "with {0} mB a tick of upkeep standing")
    @ValueSource(doubles = {0, 2.5})
    void reckonedTicksEqualTheTicksAHoldPays(double standingPerTick) {
        Walk walk = walk(RoomExtent.LAID, HOLDING, standingPerTick, base, perDistance, ringTicks);

        assertTrue(walk.extent() > RING_FIVE_WHOLE, "the holding carries the room past ring 5, stands at "
                + walk.extent());
        assertEquals(walk.ticks(), SustainedGrowth.ticksAfforded(RoomExtent.LAID, HOLDING, standingPerTick, base,
                perDistance, ringTicks));
    }

    /**
     * The closed form counts the ticks a hold walked tick by tick pays, from a whole-block mark or
     * partway into a ring on a whole mB fed, with or without upkeep, at the shipped params and at a cheap ring that
     * the ring time outlasts.
     */
    @ParameterizedTest(name = "from {0} holding {1} mB, {2} mB a tick standing, cell {3} distance {4}")
    @CsvSource({
        "4.5, 0, 0, 10, 1", "4.5, 699, 0, 10, 1", "4.5, 700, 0, 10, 1", "4.5, 701, 0, 10, 1",
        "4.5, 54321, 0, 10, 1", "4.5, 54321, 2.5, 10, 1", "4.73, 54321, 0, 10, 1", "7.21, 98765, 3, 10, 1",
        "0.5, 4000, 0, 1, 0", "0.5, 4000, 0.5, 1, 0", "1.75, 30000, 1, 0, 1", "4.5, 200000, 7, 3, 2"})
    void theClosedFormCountsTheTicksTheWalkPays(double extent, double holding, double standing, int cell,
                                               int distance) {
        assertEquals(walk(extent, holding, standing, cell, distance, ringTicks).ticks(),
                SustainedGrowth.ticksAfforded(extent, holding, standing, cell, distance, ringTicks));
    }

    /** Where a hold walked tick by tick stops, and the ticks it paid. */
    private record Walk(double extent, long ticks) {
    }

    private static Walk walk(double from, double holding, double standing, int cell, int distance, int ringTicks) {
        double extent = from;
        double left = holding;
        long paid = 0;
        while (true) {
            long feed = RoomExtent.feedFor(extent, cell, distance, ringTicks);
            if (feed + standing > left) {
                return new Walk(extent, paid);
            }
            left -= feed + standing;
            extent = RoomExtent.grown(extent, feed, cell, distance);
            paid++;
        }
    }

    /**
     * A billion mB is reckoned in closed form, not walked tick by tick, so the panel reading it
     * each frame never stalls the game.
     * decision outsider-panel-reads-the-seconds-the-void-affords
     */
    @Test
    void aBillionMillibucketsReckonsAtOnce() {
        long ticks = assertTimeoutPreemptively(WALK_WOULD_TAKE_LONGER, () -> SustainedGrowth.ticksAfforded(
                RoomExtent.LAID, BILLION, 0, base, perDistance, ringTicks));

        assertTrue(ticks > 0, "a billion mB affords growth, reckons " + ticks);
    }
}
