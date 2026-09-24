package com.mercuriusxeno.goo.tools.architecture;

import java.util.Collection;
import java.util.List;

/**
 * The outlier rule the shape graders share: a value is an outlier when it
 * exceeds what all but a twentieth of the set hold.
 */
public final class TwentiethRule {

    private static final int TWENTIETH = 20;

    /**
     * Holds only static members.
     */
    private TwentiethRule() {
    }

    /**
     * The largest value all but a twentieth of the set hold at or under. A set
     * of fewer than twenty values allows no outlier, so its threshold is its maximum.
     *
     * @param values the set's values
     * @return the threshold, 0 for an empty set
     */
    public static int threshold(Collection<Integer> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Integer> ascending = values.stream().sorted().toList();
        int allowedAbove = ascending.size() / TWENTIETH;
        return ascending.get(ascending.size() - 1 - allowedAbove);
    }
}
