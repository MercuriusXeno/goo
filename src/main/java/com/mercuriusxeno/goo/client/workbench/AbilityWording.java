package com.mercuriusxeno.goo.client.workbench;

import java.util.function.Function;

/**
 * What the Abilities tab calls each param and each mechanism, read from the lang: a param's plain label from
 * goo.ability.param.&lt;mechanism&gt;.&lt;field&gt;, its one-sentence description from the same key and .description, and
 * a mechanism's sub-heading from goo.ability.mechanism.&lt;mechanism&gt;; a key the lang holds not reads the raw path or
 * mechanism (decision ability-params-read-named-under-their-mechanism-and-hover-a-description).
 */
final class AbilityWording {
    private static final String PARAM_PREFIX = "goo.ability.param.";
    private static final String MECHANISM_PREFIX = "goo.ability.mechanism.";
    private static final String DESCRIPTION_SUFFIX = ".description";
    private static final String SEPARATOR = ".";

    private AbilityWording() {
    }

    /**
     * @param mechanism a param's mechanism
     * @param field     its field
     * @return its label's lang key
     */
    static String labelKey(String mechanism, String field) {
        return PARAM_PREFIX + mechanism + SEPARATOR + field;
    }

    /**
     * @param mechanism a param's mechanism
     * @param field     its field
     * @return its description's lang key
     */
    static String descriptionKey(String mechanism, String field) {
        return labelKey(mechanism, field) + DESCRIPTION_SUFFIX;
    }

    /**
     * @param mechanism a mechanism
     * @return its sub-heading's lang key
     */
    static String mechanismKey(String mechanism) {
        return MECHANISM_PREFIX + mechanism;
    }

    /**
     * @param leaf      a param
     * @param translate each lang key's text, the key itself where the lang holds none
     * @return its plain label, its raw path where the lang holds none
     */
    static String label(AbilitiesTab.Leaf leaf, Function<String, String> translate) {
        return translatedOr(labelKey(leaf.mechanism(), leaf.field()), leaf.path(), translate);
    }

    /**
     * @param mechanism a mechanism
     * @param translate each lang key's text, the key itself where the lang holds none
     * @return its sub-heading, the mechanism itself where the lang holds none
     */
    static String mechanismName(String mechanism, Function<String, String> translate) {
        return translatedOr(mechanismKey(mechanism), mechanism, translate);
    }

    private static String translatedOr(String key, String fallback, Function<String, String> translate) {
        String text = translate.apply(key);
        return text.equals(key) ? fallback : text;
    }
}
