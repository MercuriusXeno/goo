package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.data.GooFraction;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The order every manager value list reads in: the highest amount first, compared as the exact fraction its text
 * spells, ties by type key ascending, an amount that reads as no number after every one that does.
 * Decision value-lists-order-highest-first-ties-by-type-key.
 */
final class AmountOrder {
    private static final char DIVIDER = '/';
    private static final String WHOLE_PART_SEPARATOR = "\\s+";
    private static final int MIXED_PARTS = 2;

    /** Highest amount first, an unreadable amount last, ties by type key. */
    static final Comparator<Map.Entry<String, String>> HIGHEST_FIRST =
            Comparator.<Map.Entry<String, String>, GooFraction>comparing(entry -> amountOf(entry.getValue()),
                    Comparator.nullsLast(Comparator.<GooFraction>reverseOrder()))
                    .thenComparing(Map.Entry::getKey);

    private AmountOrder() {
    }

    /**
     * @param amounts each goo type's key with its amount as the manager shows it
     * @return the same amounts in a fresh map, highest first, ties by type key
     */
    static Map<String, String> highestFirst(Map<String, String> amounts) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(amounts.entrySet());
        entries.sort(HIGHEST_FIRST);
        Map<String, String> ordered = new LinkedHashMap<>();
        entries.forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return ordered;
    }

    /**
     * @param text an amount as the manager shows it: a whole number, n/d, or a mixed number "w n/d"
     * @return the exact amount, null where the text reads as no number
     */
    static @Nullable GooFraction amountOf(String text) {
        String[] parts = text.trim().split(WHOLE_PART_SEPARATOR);
        try {
            if (parts.length == MIXED_PARTS) {
                return GooFraction.of(Long.parseLong(parts[0])).plus(fractionOf(parts[1]));
            }
            return parts.length == 1 ? fractionOf(parts[0]) : null;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static GooFraction fractionOf(String text) {
        int divider = text.indexOf(DIVIDER);
        return divider < 0 ? GooFraction.of(Long.parseLong(text))
                : GooFraction.of(Long.parseLong(text.substring(0, divider)), Long.parseLong(text.substring(divider + 1)));
    }
}
