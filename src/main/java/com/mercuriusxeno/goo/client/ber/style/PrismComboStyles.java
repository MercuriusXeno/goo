package com.mercuriusxeno.goo.client.ber.style;

import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * The style each prism combo draws by, keyed by the id of the ability whose
 * program is the combo. Each type's thread registers its combo's style from
 * client setup; a combo with no style registered draws the plain crystal.
 * decision prism-hosts-the-combos
 */
public final class PrismComboStyles {

    private static final Map<String, PrismComboStyle> BY_COMBO = new HashMap<>();

    private PrismComboStyles() {
    }

    /**
     * Registers the style a combo draws by, replacing any earlier one.
     *
     * @param combo the id of the ability whose program is the combo
     * @param style how the combined prism draws
     */
    public static void register(String combo, PrismComboStyle style) {
        BY_COMBO.put(combo, style);
    }

    /**
     * The style a combo draws by.
     *
     * @param combo the id of the ability whose program is the combo
     * @return the style, or null for a combo that registered none
     */
    public static @Nullable PrismComboStyle forCombo(String combo) {
        return BY_COMBO.get(combo);
    }
}
