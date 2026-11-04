package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the order every manager value list reads in: the highest amount first, compared as an exact fraction, ties
 * by type key (decision value-lists-order-highest-first-ties-by-type-key).
 */
class AmountOrderTest {

    /**
     * A mixed number compares as the fraction it is: 2 reads first, then 1 1/2 and 3/2 as the equal halves they
     * are, alphabetically by type key, whatever order the source map held.
     */
    @Test
    void mixedNumberComparesAsTheFractionItIs() {
        Map<String, String> amounts = new LinkedHashMap<>();
        amounts.put("zeal", "3/2");
        amounts.put("metal", "2");
        amounts.put("aqua", "1 1/2");
        assertEquals(List.of("metal", "aqua", "zeal"), List.copyOf(AmountOrder.highestFirst(amounts).keySet()));
    }

    /**
     * An amount that reads as no number orders after every amount that does, ties by type key.
     */
    @Test
    void unreadableAmountOrdersLast() {
        Map<String, String> amounts = new LinkedHashMap<>();
        amounts.put("zeal", "?");
        amounts.put("bark", "?");
        amounts.put("metal", "1/4");
        assertEquals(List.of("metal", "bark", "zeal"), List.copyOf(AmountOrder.highestFirst(amounts).keySet()));
    }
}
