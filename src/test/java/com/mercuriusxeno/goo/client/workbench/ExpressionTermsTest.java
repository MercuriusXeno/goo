package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that an entry's value parses to terms and writes back to a string the game's loader values the same, over
 * every expression the jar's base_values.json holds (decision curated-view-shows-no-brace-quote-or-raw-id).
 */
class ExpressionTermsTest {
    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final String CONSTANTS = "_constants";
    private static final String SECTION_PREFIX = "_";
    private static final String TREE_PROBE = "goo_probe_tree_";
    private static final String SCALAR_PROBE = "goo_probe_scalar_";

    private static JsonObject baseValues() throws IOException {
        try (InputStream in = Objects.requireNonNull(ExpressionTermsTest.class.getResourceAsStream(BASE_VALUES))) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    // Each constant and entry's value rewritten through its terms; a value that fails to parse is collected.
    private static JsonObject rewritten(JsonObject file, List<String> refused) {
        JsonObject copy = file.deepCopy();
        for (Map.Entry<String, JsonElement> entry : file.entrySet()) {
            if (CONSTANTS.equals(entry.getKey())) {
                JsonObject constants = copy.getAsJsonObject(CONSTANTS);
                entry.getValue().getAsJsonObject().entrySet().forEach(constant ->
                        constants.add(constant.getKey(), roundTrip(constant.getKey(), constant.getValue(), refused)));
            } else if (!entry.getKey().startsWith(SECTION_PREFIX)) {
                copy.add(entry.getKey(), roundTrip(entry.getKey(), entry.getValue(), refused));
            }
        }
        return copy;
    }

    private static JsonElement roundTrip(String key, JsonElement value, List<String> refused) {
        Optional<ExpressionTerms.Value> terms = ExpressionTerms.parseValue(value);
        if (terms.isEmpty()) {
            refused.add(key + ": " + value);
            return value;
        }
        return ExpressionTerms.writeValue(terms.get());
    }

    // Every constant also values a probe item, as a tree and as a scalar, so a constant no item reads still counts.
    private static String probed(JsonObject file) {
        JsonObject copy = file.deepCopy();
        for (String name : file.getAsJsonObject(CONSTANTS).keySet()) {
            copy.addProperty(TREE_PROBE + name, "$" + name);
            JsonObject scalar = new JsonObject();
            scalar.addProperty("metal", "$" + name);
            copy.add(SCALAR_PROBE + name, scalar);
        }
        return copy.toString();
    }

    /**
     * Every entry, group line, constant and per-type row of the jar's file parses, and the file rewritten through
     * the terms values every item, denial and constant probe as the original does.
     */
    @Test
    void everyValueOfTheJarsFileRoundTripsToTheSameValue() throws IOException {
        JsonObject file = baseValues();
        List<String> refused = new ArrayList<>();
        JsonObject written = rewritten(file, refused);
        assertEquals(List.of(), refused);
        GooBaseValuesPreview.Loaded original = GooBaseValuesPreview.load(probed(file));
        GooBaseValuesPreview.Loaded rewrittenLoad = GooBaseValuesPreview.load(probed(written));
        assertTrue(original.baseValues().size() > file.size(), "the probes and entries all valued");
        assertEquals(original.baseValues(), rewrittenLoad.baseValues());
        assertEquals(original.denied(), rewrittenLoad.denied());
    }

    /**
     * Each form the grammar reads survives a parse and a write as its own structure.
     */
    @Nested
    class FormsSurvive {
        private ExpressionTerms.Sum parsed(String text) {
            ExpressionTerms.Sum sum = ExpressionTerms.parse(text).orElseThrow();
            assertEquals(text, ExpressionTerms.write(sum));
            return sum;
        }

        @Test
        void parenthesesHoldTheirSum() {
            ExpressionTerms.Sum sum = parsed("($medium - $food.jelly * 4) / 2");
            ExpressionTerms.Term term = sum.terms().getFirst();
            ExpressionTerms.Group group = (ExpressionTerms.Group) term.factors().getFirst().atom();
            assertEquals(2, group.sum().terms().size());
            assertEquals(ExpressionTerms.Scale.DIVIDE, term.factors().get(1).scale());
        }

        @Test
        void unaryMinusNegatesItsAtom() {
            ExpressionTerms.Sum sum = parsed("-$food.jelly");
            assertEquals(new ExpressionTerms.Negated(new ExpressionTerms.Prefab("food", "jelly")),
                    sum.terms().getFirst().factors().getFirst().atom());
        }

        @Test
        void subtractionJoinsTheTermItSubtracts() {
            ExpressionTerms.Sum sum = parsed("anvil - $iron_block");
            assertEquals(new ExpressionTerms.Item("anvil", null), sum.terms().getFirst().factors().getFirst().atom());
            assertEquals(ExpressionTerms.Join.MINUS, sum.terms().get(1).join());
        }

        @Test
        void itemTypeReadsTheItemAndItsType() {
            ExpressionTerms.Sum sum = parsed("bucket.metal");
            assertEquals(new ExpressionTerms.Item("bucket", "metal"), sum.terms().getFirst().factors().getFirst().atom());
        }

        @Test
        void prefabTypeReadsThePrefabAndItsTypeScaledByItsNumbers() {
            ExpressionTerms.Sum sum = parsed("3 $food.jelly / 2");
            List<ExpressionTerms.Factor> factors = sum.terms().getFirst().factors();
            assertEquals(new ExpressionTerms.Number("3"), factors.get(0).atom());
            assertEquals(new ExpressionTerms.Factor(ExpressionTerms.Scale.IMPLICIT,
                    new ExpressionTerms.Prefab("food", "jelly")), factors.get(1));
            assertEquals(ExpressionTerms.Scale.DIVIDE, factors.get(2).scale());
        }
    }
}
