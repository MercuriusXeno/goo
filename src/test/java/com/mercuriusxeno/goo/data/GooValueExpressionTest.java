package com.mercuriusxeno.goo.data;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the item expression evaluator on text holding no tokens: it values nothing and warns, as it does for
 * every other malformed expression, rather than throwing out of the load.
 */
class GooValueExpressionTest {

    /**
     * An empty expression values nothing.
     */
    @Test
    void emptyExpressionValuesNothing() {
        assertEquals(GooValue.EMPTY, GooValueExpression.evaluate("", Map.of(), Map.of()));
    }

    /**
     * An expression of spaces alone values nothing.
     */
    @Test
    void blankExpressionValuesNothing() {
        assertEquals(GooValue.EMPTY, GooValueExpression.evaluate("   ", Map.of(), Map.of()));
    }

    /**
     * An expression ending on an operator keeps what came before it.
     */
    @Test
    void trailingOperatorKeepsTheLeftOperand() {
        Map<String, GooValue> trees = Map.of("leaf", GooValueJsonFormat.parseGooValue(
                JsonParser.parseString("{\"leaf\": 2}").getAsJsonObject(), Map.of(), null, Map.of()));
        assertEquals(trees.get("leaf"), GooValueExpression.evaluate("$leaf +", Map.of(), Map.of(), trees));
    }
}
