package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coat step reads the ticks of a stream's hold, which needs the channel,
 * and refuses a step naming none (decision bounce-leaves-the-mod-and-goop-stands-alone).
 */
class CoatStepTest {

    @Test
    void ticksDecodeAndNeedTheChannel() {
        assertEquals(Set.of(HostCapability.CHANNEL), decoded("{\"ticks\": 20}").getOrThrow().requires());
    }

    @Test
    void noTicksRefuse() {
        assertTrue(decoded("{}").isError(), "a coat step naming no ticks should refuse");
    }

    private static DataResult<CoatStep> decoded(String json) {
        return CoatStep.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }
}
