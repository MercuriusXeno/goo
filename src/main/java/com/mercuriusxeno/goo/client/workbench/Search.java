package com.mercuriusxeno.goo.client.workbench;

import org.jspecify.annotations.Nullable;
import java.util.Locale;

/**
 * The one search rule every tab box and every dropdown matches by: the query trimmed and its case ignored, matched as
 * a substring of a candidate's name or id; a leading $ narrows to prefabs and a leading # to groups and tags, the
 * mark stripped before matching (decision one-search-rule-serves-every-box-and-dropdown).
 */
final class Search {
    private static final String PREFAB_MARK = "$";
    private static final String GROUP_MARK = "#";
    /** The query every candidate matches. */
    static final Query EVERYTHING = new Query("", null);

    private Search() {
    }

    /**
     * What a candidate is, which a marked query narrows to.
     */
    enum Kind {
        /** An item or anything else a mark never names. */
        ITEM,
        /** A prefab, which a $ query names. */
        PREFAB,
        /** A group or an item tag, which a # query names. */
        GROUP
    }

    /**
     * A query read.
     *
     * @param text     the text matched, trimmed, folded and stripped of its mark
     * @param narrowed the kind its mark narrows to, null for every kind
     */
    record Query(String text, @Nullable Kind narrowed) {
    }

    /**
     * @param typed what a search box holds
     * @return the query it reads as
     */
    static Query query(String typed) {
        String folded = fold(typed.trim());
        if (folded.startsWith(PREFAB_MARK)) {
            return new Query(folded.substring(PREFAB_MARK.length()), Kind.PREFAB);
        }
        if (folded.startsWith(GROUP_MARK)) {
            return new Query(folded.substring(GROUP_MARK.length()), Kind.GROUP);
        }
        return new Query(folded, null);
    }

    /**
     * @param query     the query
     * @param kind      what the candidate is
     * @param namesAndIds the candidate's name, its id and anything else it is found by; a count is never among them
     * @return whether the candidate stands under the query
     */
    static boolean matches(Query query, Kind kind, String... namesAndIds) {
        if (query.narrowed() != null && query.narrowed() != kind) {
            return false;
        }
        if (query.text().isEmpty()) {
            return true;
        }
        for (String text : namesAndIds) {
            if (fold(text).contains(query.text())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param text words
     * @return them with their case folded, as every match compares them
     */
    static String fold(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
