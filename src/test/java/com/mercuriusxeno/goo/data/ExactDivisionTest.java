package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Authored arithmetic divides exactly: a prefab, a typed prefab's amount and an item's amount each keep the fraction
 * a division leaves, and a later step reads that fraction rather than its floor (decision
 * values-are-fractions-melt-floors-fabricate-ceils).
 */
class ExactDivisionTest {
    private static final String FILE = """
            {
                "_constants": {
                    "base": 6,
                    "saturation": "$base / 8",
                    "typed": {"jelly": "$base / 8"}
                },
                "_groups": {}, "_restricted": [], "_conversions": {}, "_post_conversions": {},
                "apple": {"jelly": "$saturation"},
                "bread": {"jelly": "$base / 4 * 2"},
                "carrot": "$typed"
            }
            """;

    private static GooFraction jellyOf(String item) {
        return GooBaseValuesPreview.load(FILE).baseValues().get(Identifier.withDefaultNamespace(item))
                .exact(GooTypes.JELLY);
    }

    /**
     * $base / 8 with $base at 6 is the prefab 3/4.
     */
    @Test
    void scalarPrefabKeepsItsFraction() {
        JsonObject constants = JsonParser.parseString(FILE).getAsJsonObject().getAsJsonObject("_constants");
        Map<String, String> scalars = new java.util.HashMap<>();
        GooConstantResolver.resolve(constants)
                .forEach(constant -> scalars.put(constant.name(), String.valueOf(constant.scalar())));
        assertEquals("3/4", scalars.get("saturation"));
    }

    /**
     * An item amount reading the 3/4 prefab holds 3/4.
     */
    @Test
    void itemAmountReadsThePrefabsFraction() {
        assertEquals(GooFraction.of(3, 4), jellyOf("apple"));
    }

    /**
     * $base / 4 * 2 is 3, the 3/2 the division leaves carried into the product rather than floored to 1.
     */
    @Test
    void divisionCarriesItsFractionIntoTheNextStep() {
        assertEquals(GooFraction.of(3), jellyOf("bread"));
    }

    /**
     * A typed prefab's amount divides exactly as well.
     */
    @Test
    void typedPrefabAmountKeepsItsFraction() {
        assertEquals(GooFraction.of(3, 4), jellyOf("carrot"));
    }
}
