package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The slick step names its ramp alone, with no ticks to build a cell over,
 * since a slick cell stands the moment its drip lands, and needs the channel
 * (decisions drip-creates-a-volume-cell-immediately, slick-resolves-faster-by-speed-over-unadjusted-speed).
 */
class SlickStepTest {

    private static final String RAMP_PARAMS = "{\"start_ramp\": 1.03, \"strongest_ramp\": 1.08, \"build_ticks\": 40,"
            + " \"top_speed\": 1.0, \"start_speed\": 0.3}";

    @Test
    void theRampDecodesWithNoTicksAndNeedsTheChannel() {
        SlickStep slick = SlickStep.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(RAMP_PARAMS))
                .getOrThrow();

        assertEquals(new SlickStep(1.03, 1.08, 40, 1.0, 0.3), slick);
        assertEquals(Set.of(HostCapability.CHANNEL), slick.requires());
    }
}
