package com.mercuriusxeno.goo.client.workbench;

import java.util.function.IntUnaryOperator;

/**
 * How many used-by items one line draws: every item where they fit, else as many as leave room for an
 * "... and N more" link that expands the line (decision used-by-draws-the-terminus-items-alone).
 */
final class UsedByLine {
    private static final String MORE_START = "... and ";
    private static final String MORE_END = " more";

    private UsedByLine() {
    }

    /**
     * @param shown  how many items the line draws
     * @param hidden how many it leaves to the "more" link, none where every item draws
     */
    record Fit(int shown, int hidden) {
    }

    /**
     * @param count     how many items the constant reaches
     * @param width     the width the line fills
     * @param step      the width one item takes with its gap
     * @param moreWidth the width of the "more" link counting a number of hidden items
     * @return how many items draw and how many the link counts
     */
    static Fit fit(int count, int width, int step, IntUnaryOperator moreWidth) {
        if ((long) count * step <= width) {
            return new Fit(count, 0);
        }
        int shown = Math.max(0, Math.min(count, (width - moreWidth.applyAsInt(count)) / step));
        return new Fit(shown, count - shown);
    }

    /**
     * @param count how many items the constant reaches
     * @return every item drawn, as an expanded line draws them
     */
    static Fit expanded(int count) {
        return new Fit(count, 0);
    }

    /**
     * @param hidden how many items the link counts
     * @return the link's label
     */
    static String moreLabel(int hidden) {
        return MORE_START + hidden + MORE_END;
    }
}
