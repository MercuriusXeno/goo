package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The FORMULAS index names, under each constant and each referenced item, exactly the
 * entries whose raw expression consumes it, by $name, dot form and bare item name alike.
 */
class GooFormulaReferencesTest {

    private static final String FIXTURE = """
            {
              "_constants": {"base": 192, "fuel": {"blaze": "$base"}},
              "coal": "7 $fuel",
              "charcoal": "coal",
              "lava_bucket": {"blaze": "4 $fuel.blaze"}
            }
            """;

    @Test
    void everyOperandNamesExactlyItsConsumers() {
        JsonObject merged = JsonParser.parseString(FIXTURE).getAsJsonObject();
        assertEquals(Map.of(
                        "$base", Set.of("$fuel"),
                        "$fuel", Set.of("minecraft:coal", "minecraft:lava_bucket"),
                        "minecraft:coal", Set.of("minecraft:charcoal")),
                GooFormulaReferences.consumersByOperand(merged));
    }

    @Test
    void itemDotFormCountsAsItsItem() {
        JsonObject merged = JsonParser.parseString("""
                {"coal": {"blaze": 8}, "torch": {"blaze": "coal.blaze / 4"}}
                """).getAsJsonObject();
        assertEquals(Map.of("minecraft:coal", Set.of("minecraft:torch")),
                GooFormulaReferences.consumersByOperand(merged));
    }
}
