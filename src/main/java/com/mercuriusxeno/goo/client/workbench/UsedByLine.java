package com.mercuriusxeno.goo.client.workbench;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/**
 * How many used-by items one line draws: every item where they fit, else as many as leave room for an
 * ..+n button that expands the line (decision used-by-draws-the-terminus-items-alone), and the Hide
 * button an expanded line ends on (decision expanded-used-by-lists-take-a-hide-button).
 */
final class UsedByLine {
    /** The label of the button that collapses an expanded list. */
    static final String HIDE = "Hide";
    // Decision more-button-reads-dot-dot-plus-n.
    private static final String MORE_START = "..+";

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
     * A line whose slots each take their own width, an item icon or a link chip.
     * Decision used-by-lists-direct-items-and-links-to-prefabs-and-groups.
     *
     * @param widths    each slot's width, in draw order
     * @param gap       the room after each slot
     * @param width     the width the line fills
     * @param moreWidth the width of the "more" link counting a number of hidden slots
     * @return how many slots draw and how many the link counts
     */
    static Fit fitSlots(List<Integer> widths, int gap, int width, IntUnaryOperator moreWidth) {
        int count = widths.size();
        if (widths.stream().mapToLong(slot -> slot + gap).sum() <= width) {
            return new Fit(count, 0);
        }
        int room = width - moreWidth.applyAsInt(count);
        int shown = 0;
        int used = 0;
        while (shown < count && used + widths.get(shown) + gap <= room) {
            used += widths.get(shown) + gap;
            shown++;
        }
        return new Fit(shown, count - shown);
    }

    /**
     * @param widths   each drawn slot's width, in draw order
     * @param gap      the room after each slot
     * @param width    the width the list fills
     * @param endWidth the width of the button the list ends on, zero for none
     * @return where each slot stands, then where the end button stands, each slot wrapping to a new row where it
     *     would pass the width
     */
    static List<Slot> flow(List<Integer> widths, int gap, int width, int endWidth) {
        List<Slot> slots = new ArrayList<>();
        int x = 0;
        int row = 0;
        for (int slot : widths) {
            if (x > 0 && x + slot > width) {
                x = 0;
                row++;
            }
            slots.add(new Slot(x, row));
            x += slot + gap;
        }
        slots.add(x > 0 && x + endWidth > width ? new Slot(0, row + 1) : new Slot(x, row));
        return slots;
    }

    /**
     * @param flowed what {@link #flow} answered: each slot, then the end button
     * @param ended  whether the list draws its end button
     * @return the last row anything drawn stands on
     */
    static int lastRow(List<Slot> flowed, boolean ended) {
        List<Slot> drawn = ended ? flowed : flowed.subList(0, Math.max(1, flowed.size() - 1));
        return drawn.getLast().row();
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
        return MORE_START + hidden;
    }

    /**
     * @param fit      how the line drew its items
     * @param expanded whether the list stands expanded
     * @return the button the list ends on: Hide for an expanded list, the hidden count for a collapsed one, none for
     *     a line every item fits
     */
    static Optional<String> endLabel(Fit fit, boolean expanded) {
        if (expanded) {
            return Optional.of(HIDE);
        }
        return fit.hidden() > 0 ? Optional.of(moreLabel(fit.hidden())) : Optional.empty();
    }

    /**
     * Where an expanded list's Hide button stands.
     *
     * @param x   its offset from the list's left edge
     * @param row the row it stands on, from 0
     */
    record Slot(int x, int row) {
    }

    /**
     * @param fit       how the line drew its items
     * @param expanded  whether the list stands expanded
     * @param perRow    how many items an expanded row holds
     * @param step      the width one item takes with its gap
     * @param width     the width the list fills
     * @param hideWidth the Hide button's width
     * @return where the list's end button stands: past a collapsed line's last item, or Hide's slot
     */
    static Slot endSlot(Fit fit, boolean expanded, int perRow, int step, int width, int hideWidth) {
        return expanded ? hideSlot(fit.shown(), perRow, step, width, hideWidth) : new Slot(fit.shown() * step, 0);
    }

    /**
     * @param shown     how many items the expanded list draws
     * @param perRow    how many items a row holds
     * @param step      the width one item takes with its gap
     * @param width     the width the list fills
     * @param hideWidth the Hide button's width
     * @return the slot after the last row's last item, or the start of a row of its own where no room is left
     */
    static Slot hideSlot(int shown, int perRow, int step, int width, int hideWidth) {
        if (perRow <= 0) {
            return new Slot(0, shown);
        }
        int row = shown == 0 ? 0 : (shown - 1) / perRow;
        int after = (shown - row * perRow) * step;
        return after + hideWidth <= width ? new Slot(after, row) : new Slot(0, row + 1);
    }
}
