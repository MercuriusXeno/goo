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
     * stripped_stem's shroom reading $stem.leaf, stem holding shroom and nether, draws one line naming stem and leaf.
     */
    @Test
    void typedPrefabFieldReadingALackedTypeWarnsNamingTheReferentAndTheType() {
        List<String> warnings = warningsOver("""
                {"_constants": {"stem": {"shroom": 2, "nether": 2},
                                "stripped_stem": {"shroom": "$stem.leaf / 2"}}}
                """);
        assertEquals(List.of("_constants.stripped_stem.shroom: stem holds no leaf"), warnings);
    }

    /**
     * A prefab in string form and an item entry reading $stem.leaf each draw the line under their own context.
     */
    @Test
    void stringPrefabAndItemReadingALackedTypeWarn() {
        List<String> warnings = warningsOver("""
                {"_constants": {"stem": {"shroom": 2, "nether": 2}, "half_stem": "$stem.leaf / 2"},
                 "stick": {"shroom": "$stem.leaf"}}
                """);
        assertEquals(List.of("_constants.half_stem: stem holds no leaf", "stick.shroom: stem holds no leaf"),
                warnings);
    }

    /**
     * The same reads of a type stem holds draw no warning.
     */
    @Test
    void readingAHeldTypeDrawsNoWarning() {
        List<String> warnings = warningsOver("""
                {"_constants": {"stem": {"shroom": 2, "nether": 2},
                                "stripped_stem": {"shroom": "$stem.shroom / 2"}, "half_stem": "$stem.nether / 2"},
                 "stick": {"shroom": "$stem.shroom"}}
                """);
        assertTrue(warnings.isEmpty(), String.valueOf(warnings));
    }

    /**
     * nether_gold_ore reading the item gold_ingot, which a prefab also names and no line above values, draws no
     * missing-$ warning: a bare word naming a registered item reads as that item
     * (decision diagnose-and-correct-item-reference-unrecognized-token).
     */
    @Test
    void bareWordNamingARegisteredItemAndAPrefabDrawsNoMissingDollarWarning() {
        List<String> warnings = warningsOver("""
                {"_constants": {"gold_nugget": 1, "gold_ingot": "9 $gold_nugget", "mundane_block": {"rock": 2}},
                 "nether_gold_ore": "gold_ingot + $mundane_block"}
                """);
        assertTrue(warnings.isEmpty(), String.valueOf(warnings));
    }

    /**
     * A bare word naming a prefab and no registered item still reads as a prefab missing its $.
     */
    @Test
    void bareWordNamingAPrefabAndNoItemWarnsOfTheMissingDollar() {
        List<String> warnings = warningsOver("""
                {"_constants": {"mundane_block": {"rock": 2}},
                 "coal_ore": "4 coal + mundane_block"}
                """);
        assertEquals(List.of("coal_ore: 'mundane_block' looks like a constant missing its $ prefix (should be"
                + " $mundane_block)"), warnings);
    }
}
