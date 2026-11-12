package com.mercuriusxeno.goo.client.hud;

/**
 * A canvas that joins each fill to the one before it where the two share a
 * color and sit side by side, edge to edge, sharing that whole edge, so a
 * run of pixels draws as one rectangle covering the same pixels at the same
 * colors; a fill covering no pixel is dropped.
 * decision diagnose-sculkers-lag-and-prove-it-improved-significantly
 */
final class AdjacentFills implements VignetteCanvas {

    private final VignetteCanvas target;
    private boolean pending;
    private int left;
    private int top;
    private int right;
    private int bottom;
    private int color;

    /**
     * @param target the canvas the joined fills draw on
     */
    AdjacentFills(VignetteCanvas target) {
        this.target = target;
    }

    @Override
    public int guiWidth() {
        return target.guiWidth();
    }

    @Override
    public int guiHeight() {
        return target.guiHeight();
    }

    @Override
    public void fill(int minX, int minY, int maxX, int maxY, int fillColor) {
        int fillLeft = Math.min(minX, maxX);
        int fillRight = Math.max(minX, maxX);
        int fillTop = Math.min(minY, maxY);
        int fillBottom = Math.max(minY, maxY);
        if (fillLeft != fillRight && fillTop != fillBottom
                && !joined(fillLeft, fillTop, fillRight, fillBottom, fillColor)) {
            flush();
            pending = true;
            left = fillLeft;
            top = fillTop;
            right = fillRight;
            bottom = fillBottom;
            color = fillColor;
        }
    }

    /**
     * Joins a fill to the one being joined, where it shares its color and a whole edge.
     *
     * @param fillLeft   the fill's left edge
     * @param fillTop    its top edge
     * @param fillRight  its right edge, exclusive
     * @param fillBottom its bottom edge, exclusive
     * @param fillColor  its color, ARGB
     * @return true where it joined
     */
    private boolean joined(int fillLeft, int fillTop, int fillRight, int fillBottom, int fillColor) {
        if (!pending || fillColor != color) {
            return false;
        }
        if (joinsAcross(fillLeft, fillTop, fillRight, fillBottom)) {
            left = Math.min(left, fillLeft);
            right = Math.max(right, fillRight);
            return true;
        }
        if (joinsDown(fillLeft, fillTop, fillRight, fillBottom)) {
            top = Math.min(top, fillTop);
            bottom = Math.max(bottom, fillBottom);
            return true;
        }
        return false;
    }

    private boolean joinsAcross(int fillLeft, int fillTop, int fillRight, int fillBottom) {
        return fillTop == top && fillBottom == bottom && (fillLeft == right || fillRight == left);
    }

    private boolean joinsDown(int fillLeft, int fillTop, int fillRight, int fillBottom) {
        return fillLeft == left && fillRight == right && (fillTop == bottom || fillBottom == top);
    }

    /** Draws the fill still being joined, once no more fills follow. */
    void flush() {
        if (pending) {
            target.fill(left, top, right, bottom, color);
            pending = false;
        }
    }
}
