package com.mercuriusxeno.goo.client.machine;

import com.mercuriusxeno.goo.item.GooContents;
import org.jspecify.annotations.Nullable;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/**
 * The span of one vertical vat column, bottom and top inclusive. The walk
 * follows the VAT_ABOVE and VAT_BELOW links VatBlock maintains, and crosses a
 * link only onto a block that is still a vat, so a link left stale for the
 * tick after a vat breaks ends the column instead of bridging the gap
 * (decision one-panel-painter-takes-rows).
 *
 * @param bottomY the lowest member's Y
 * @param topY    the highest member's Y
 */
public record VatColumn(int bottomY, int topY) {

    /**
     * Walks the column holding the vat at startY.
     *
     * @param startY      the Y of a vat in the column
     * @param isVat       whether the block at a Y is a vat
     * @param linkedAbove whether the vat at a Y carries VAT_ABOVE
     * @param linkedBelow whether the vat at a Y carries VAT_BELOW
     * @return the column's span
     */
    public static VatColumn walk(int startY, IntPredicate isVat,
                                 IntPredicate linkedAbove, IntPredicate linkedBelow) {
        int top = startY;
        while (linkedAbove.test(top) && isVat.test(top + 1)) {
            top++;
        }
        int bottom = startY;
        while (linkedBelow.test(bottom) && isVat.test(bottom - 1)) {
            bottom--;
        }
        return new VatColumn(bottom, top);
    }

    /**
     * Returns the number of vats in the column.
     *
     * @return the column height in blocks
     */
    public int size() {
        return topY - bottomY + 1;
    }

    /**
     * Returns a member's index counted from the bottom.
     *
     * @param y the member's Y
     * @return 0 for the bottom member
     */
    public int indexFromBottom(int y) {
        return y - bottomY;
    }

    /**
     * Sums every member's contents, bottom to top.
     *
     * @param contentsAt a member's contents by Y, or null where the vat has no block entity
     * @return the summed contents
     */
    public GooContents sum(IntFunction<@Nullable GooContents> contentsAt) {
        GooContents sum = GooContents.EMPTY;
        for (int y = bottomY; y <= topY; y++) {
            GooContents contents = contentsAt.apply(y);
            if (contents != null) {
                sum = sum.mergeWith(contents);
            }
        }
        return sum;
    }
}
