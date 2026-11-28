package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The goop step decodes with no params and needs the channel
 * (decisions bounce-leaves-the-mod-and-goop-stands-alone, drip-creates-a-volume-cell-immediately).
 */
class GoopStepTest {

    @Test
    void aStepNamingNoParamsDecodesAndNeedsTheChannel() {
        GoopStep goop = GoopStep.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("{}")).getOrThrow();

        assertEquals(Set.of(HostCapability.CHANNEL), goop.requires());
    }
}
