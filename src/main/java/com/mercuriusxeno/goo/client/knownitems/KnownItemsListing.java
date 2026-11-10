package com.mercuriusxeno.goo.client.knownitems;

import com.mercuriusxeno.goo.client.knownitems.KnownItemsOverlayRenderer.KnownItem;
import java.text.Collator;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The order and the search of the known items overlay: items sort by their display name under the
 * collator of the client's language, and a query keeps the names containing it, case ignored.
 * decision menu-lists-known-items-alphabetically-with-search-and-scroll
 */
public final class KnownItemsListing {

    private KnownItemsListing() {}

    /**
     * Sorts the items by display name as the language reads them.
     *
     * @param items  the known items
     * @param locale the client's language
     * @return the items in the language's alphabetical order
     */
    public static List<KnownItem> order(Collection<KnownItem> items, Locale locale) {
        Collator collator = Collator.getInstance(locale);
        return items.stream().sorted(Comparator.comparing(KnownItemsListing::displayName, collator)).toList();
    }

    /**
     * Keeps the items whose display name contains the query, case ignored.
     *
     * @param items  the ordered items
     * @param query  the typed text
     * @param locale the client's language, which folds case
     * @return the matching items in their order, every item for an empty query
     */
    public static List<KnownItem> filter(List<KnownItem> items, String query, Locale locale) {
        if (query.isEmpty()) {
            return items;
        }
        String folded = query.toLowerCase(locale);
        return items.stream().filter(item -> displayName(item).toLowerCase(locale).contains(folded)).toList();
    }

    /**
     * The name an item sorts and matches by: its display name in the client's language.
     *
     * @param item the known item
     * @return the item's display name
     */
    static String displayName(KnownItem item) {
        return item.stack().getHoverName().getString();
    }

    /**
     * The Java locale of a Minecraft language code such as "en_us".
     *
     * @param languageCode the selected language's code
     * @return the locale the code names
     */
    public static Locale localeOf(String languageCode) {
        return Locale.forLanguageTag(languageCode.replace('_', '-'));
    }
}
