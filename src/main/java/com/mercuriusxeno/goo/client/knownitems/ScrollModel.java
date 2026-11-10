package com.mercuriusxeno.goo.client.knownitems;

/**
 * The known items grid's scroll: which row shows first, clamped so the last row never scrolls past
 * the bottom, and where the scroll bar's thumb sits on its track.
 * decision menu-lists-known-items-alphabetically-with-search-and-scroll
 *
 * @param rowCount    the grid's rows
 * @param rowsVisible the rows the frame shows at once
 * @param firstRow    the first row shown
 */
public record ScrollModel(int rowCount, int rowsVisible, int firstRow) {

    /** The shortest a thumb draws, so a long list keeps a thumb to grab. */
    static final int MIN_THUMB = 8;

    /**
     * Builds the scroll with its first row clamped to the rows.
     *
     * @param rowCount    the grid's rows
     * @param rowsVisible the rows the frame shows at once
     * @param firstRow    the first row asked for
     * @return the scroll
     */
    public static ScrollModel of(int rowCount, int rowsVisible, int firstRow) {
        return new ScrollModel(rowCount, rowsVisible, Math.clamp(firstRow, 0, Math.max(0, rowCount - rowsVisible)));
    }

    /**
     * The last row that may show first.
     *
     * @return the first row of the bottom-most page
     */
    public int maxFirstRow() {
        return Math.max(0, rowCount - rowsVisible);
    }

    /**
     * Whether the grid holds more rows than show, so the scroll bar draws.
     *
     * @return true when some rows are out of view
     */
    public boolean scrolls() {
        return rowCount > rowsVisible;
    }

    /**
     * The scroll moved by some rows, clamped to the rows.
     *
     * @param rows the rows to move, down for positive
     * @return the moved scroll
     */
    public ScrollModel scrolledBy(int rows) {
        return of(rowCount, rowsVisible, firstRow + rows);
    }

    /**
     * The thumb's length on a track.
     *
     * @param track the track's length
     * @return the thumb's length, the visible share of the rows
     */
    public int thumbSpan(int track) {
        return scrolls() ? Math.max(MIN_THUMB, track * rowsVisible / rowCount) : track;
    }

    /**
     * The thumb's offset from the track's start.
     *
     * @param track the track's length
     * @return how far down the track the thumb starts
     */
    public int thumbOffset(int track) {
        return scrolls() ? (track - thumbSpan(track)) * firstRow / maxFirstRow() : 0;
    }

    /**
     * The scroll with its thumb dragged to an offset on the track.
     *
     * @param offset the thumb's offset from the track's start
     * @param track  the track's length
     * @return the scroll whose thumb sits nearest that offset
     */
    public ScrollModel withThumbAt(double offset, int track) {
        int travel = track - thumbSpan(track);
        if (travel <= 0) {
            return this;
        }
        return of(rowCount, rowsVisible, (int) Math.round(offset * maxFirstRow() / travel));
    }
}
