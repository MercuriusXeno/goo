package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coat step reads one of two deliveries: ticks for a stream's hold,
 * which needs the channel, or layers for a splat, which needs a cell to lay
 * them in, and refuses a step naming both or neither
 * (decision bounce-throw-dumps-layers-that-flow-out).
 */
class CoatStepTest {

    @Test
    void layersAloneDecodeAndNeedACellToLayThemIn() {
        assertEquals(Set.of(HostCapability.PLACE_BLOCK), decoded("{\"layers\": 4}").getOrThrow().requires());
    }

    @Test
    void ticksAloneDecodeAndNeedTheChannel() {
        assertEquals(Set.of(HostCapability.CHANNEL), decoded("{\"ticks\": 20}").getOrThrow().requires());
    }

    @Test
    void bothTicksAndLayersRefuse() {
        assertTrue(decoded("{\"ticks\": 20, \"layers\": 4}").isError(), "a coat step naming both should refuse");
    }

    @Test
    void neitherTicksNorLayersRefuse() {
        assertTrue(decoded("{}").isError(), "a coat step naming neither should refuse");
    }

    private static DataResult<CoatStep> decoded(String json) {
        return CoatStep.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }
}
