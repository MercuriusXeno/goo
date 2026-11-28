package com.mercuriusxeno.goo.tools;

import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that nether, void, sculk and astral run faster and churn more than their entries on 26.3, the values each
 * row below holds.
 */
class DarkGooChurnTest {

    // dark-goos-churn-as-quantum-does
    @ParameterizedTest
    @CsvSource({"nether, 3, 0.04, 0.006", "void, 4, 0.04, 0.005", "sculk, 3, 0.05, 0.007", "astral, 4, 0.03, 0.006"})
    void typeRunsFasterAndChurnsMoreThanOn263(String type, int formerFrametime, float formerDecayRate,
                                               float formerIgnitionChance) throws IOException {
        JsonObject entry = DarkGooResources.fluidEntry(type);
        JsonObject genParams = entry.getAsJsonObject("genParams");
        int frametime = entry.get("frametime").getAsInt();
        float decayRate = genParams.get("decayRate").getAsFloat();
        float ignitionChance = genParams.get("ignitionChance").getAsFloat();
        assertTrue(frametime < formerFrametime, type + "'s frametime " + frametime + " is not below " + formerFrametime);
        assertTrue(decayRate > formerDecayRate, type + "'s decayRate " + decayRate + " is not above " + formerDecayRate);
        assertTrue(ignitionChance > formerIgnitionChance,
                type + "'s ignitionChance " + ignitionChance + " is not above " + formerIgnitionChance);
    }
}
