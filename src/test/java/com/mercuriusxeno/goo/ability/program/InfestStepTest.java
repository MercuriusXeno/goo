package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonElement;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the infest step's field set as zombie_infest.json ships it: the
 * block map, the cadence and the bite's damage, and no chance beside them.
 * infest-has-a-chance-to-deplete-as-it-gnaws-and-bites
 */
class InfestStepTest {

    @Test
    void theShippedStepEncodesTheMapTheCadenceAndTheDamage() {
        InfestStep shipped = InfestStep.in(AbilityJson.decode("zombie_infest").behaviors()).orElseThrow();

        JsonElement encoded = InfestStep.CODEC.codec().encodeStart(JsonOps.INSTANCE, shipped).getOrThrow();

        assertEquals(Set.of("map", "gnaw_ticks", "damage"), encoded.getAsJsonObject().keySet());
    }
}
