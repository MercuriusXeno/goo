package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nitro's blast grows with the goo's quantity: the power a cell explodes at
 * rises with its layers by the tunables unstable_nitro.json carries.
 * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
 */
class NitroStepTest {

    private static final String NITRO = "unstable_nitro";
    private static final float TOLERANCE = 1e-6f;

    private static NitroStep shipped() {
        return NitroStep.in(AbilityJson.decode(NITRO).behaviors()).orElseThrow();
    }

    private static JsonObject shippedTunables() {
        return JsonParser.parseString(AbilityJson.read(NITRO)).getAsJsonObject().getAsJsonArray("behaviors")
                .asList().stream().map(element -> element.getAsJsonObject())
                .filter(step -> "nitro".equals(step.get("type").getAsString())).findFirst().orElseThrow();
    }

    @Test
    void aFullCellBlastsHarderThanAOneLayerCell() {
        NitroStep nitro = shipped();

        assertTrue(nitro.powerAt(VolumeCellBlock.MAX_LAYERS) > nitro.powerAt(1),
                nitro.powerAt(VolumeCellBlock.MAX_LAYERS) + " against " + nitro.powerAt(1));
    }

    @Test
    void eachCellsPowerIsTheShippedTunablesAppliedToItsLayers() {
        NitroStep nitro = shipped();
        JsonObject tunables = shippedTunables();
        double base = tunables.get("base_power").getAsDouble();
        double perLayer = tunables.get("power_per_layer").getAsDouble();

        assertEquals((float) (base + perLayer), nitro.powerAt(1), TOLERANCE);
        assertEquals((float) (base + perLayer * VolumeCellBlock.MAX_LAYERS), nitro.powerAt(VolumeCellBlock.MAX_LAYERS),
                TOLERANCE);
    }
}
