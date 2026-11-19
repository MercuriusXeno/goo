package com.mercuriusxeno.goo.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the static validator's warning on a $name.type token whose object-form prefab holds no key for that type,
 * the warning the audit's expression-warnings rule lists
 * (decision type-the-referent-lacks-loads-no-value-and-reads-a-red-line).
 */
class GooValueValidatorTest {

    private static List<String> warningsOver(String json) {
        List<String> warnings = new ArrayList<>();
        GooValueValidator.validateJson(JsonParser.parseString(json).getAsJsonObject(), warnings);
        return warnings;
    }

    /**
     * stripped_stem's sculk reading $stem.leaf, stem holding sculk and nether, draws one line naming stem and leaf.
     */
    @Test
    void typedPrefabFieldReadingALackedTypeWarnsNamingTheReferentAndTheType() {
        List<String> warnings = warningsOver("""
                {"_constants": {"stem": {"sculk": 2, "nether": 2},
                                "stripped_stem": {"sculk": "$stem.leaf / 2"}}}
                """);
        assertEquals(List.of("_constants.stripped_stem.sculk: stem holds no leaf"), warnings);
    }

    /**
     * A prefab in string form and an item entry reading $stem.leaf each draw the line under their own context.
     */
    @Test
    void stringPrefabAndItemReadingALackedTypeWarn() {
        List<String> warnings = warningsOver("""
                {"_constants": {"stem": {"sculk": 2, "nether": 2}, "half_stem": "$stem.leaf / 2"},
                 "stick": {"sculk": "$stem.leaf"}}
                """);
        assertEquals(List.of("_constants.half_stem: stem holds no leaf", "stick.sculk: stem holds no leaf"),
                warnings);
    }

    /**
     * The same reads of a type stem holds draw no warning.
     */
    @Test
    void readingAHeldTypeDrawsNoWarning() {
        List<String> warnings = warningsOver("""
                {"_constants": {"stem": {"sculk": 2, "nether": 2},
                                "stripped_stem": {"sculk": "$stem.sculk / 2"}, "half_stem": "$stem.nether / 2"},
                 "stick": {"sculk": "$stem.sculk"}}
                """);
        assertTrue(warnings.isEmpty(), String.valueOf(warnings));
    }
}
