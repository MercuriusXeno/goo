package com.mercuriusxeno.goo.ability.pocket;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * The entries Pocket's search bar leaves standing: those whose item name
 * holds the typed text, case aside; a blank search shows them all.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 */
public final class PocketSearch {

    private PocketSearch() {
    }

    /**
     * The entries whose item name holds the query.
     *
     * @param names each entry's item name, in shelf order
     * @param query the search bar's text
     * @return the indices of the entries shown, in shelf order
     */
    public static List<Integer> visible(List<String> names, String query) {
        String sought = query.strip().toLowerCase(Locale.ROOT);
        return IntStream.range(0, names.size())
                .filter(index -> names.get(index).toLowerCase(Locale.ROOT).contains(sought))
                .boxed().toList();
    }
}
