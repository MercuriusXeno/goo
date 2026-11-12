package com.mercuriusxeno.goo.ability.outsider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private final JsonObject outsider = JsonParser.parseString(AbilityJson.read("void_outsider")).getAsJsonObject()
            .getAsJsonArray("behaviors").get(0).getAsJsonObject();
    private final int ringTicks = outsider.get("ring_ticks").getAsInt();
    private final int base = outsider.get("cell_cost").getAsInt();
    private final int perDistance = outsider.get("distance_cost").getAsInt();

    @ParameterizedTest(name = "with {0} mB a tick of upkeep standing")
    @ValueSource(doubles = {0, 2.5})
    void reckonedTicksEqualTheTicksAHoldPays(double standingPerTick) {
        double extent = RoomExtent.LAID;
        double left = HOLDING;
        long paid = 0;
        while (true) {
            long feed = RoomExtent.feedFor(extent, base, perDistance, ringTicks);
            if (feed + standingPerTick > left) {
                break;
            }
            left -= feed + standingPerTick;
            extent = RoomExtent.grown(extent, feed, base, perDistance);
            paid++;
        }

        assertTrue(extent > RING_FIVE_WHOLE, "the holding carries the room past ring 5, stands at " + extent);
        assertEquals(paid, SustainedGrowth.ticksAfforded(RoomExtent.LAID, HOLDING, standingPerTick, base,
                perDistance, ringTicks));
    }
}
