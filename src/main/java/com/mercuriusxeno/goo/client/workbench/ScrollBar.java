package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One scrolling area's offset and the bar drawn at its right edge: the thumb's height is the view's share of the
 * content and its top the offset's share of the track, so the bar tells how long the list is and where the view
 * stands; the wheel steps the offset and a drag on the track moves it, and the clamp to the content lives here alone
 * (decision every-tab-draws-a-scroll-bar). Content, view and offset share one unit, pixels or rows, as the area
 * scrolls.
 */
final class ScrollBar {
    /** The track's width, which a pane leaves free at its area's right edge. */
    static final int WIDTH = 4;
    /** The gap a pane keeps between its content and the track. */
    static final int GAP = 2;
    private static final int MIN_THUMB = 4;
    private static final double HALF = 0.5;
    private static final int TRACK_COLOR = 0x40FFFFFF;
    private static final int THUMB_COLOR = 0xC0C0C0C0;

    private final int step;
    private final boolean tails;
    private boolean following;
    private int offset;
    private int maximum;
    private int view;

    /**
     * Where the thumb stands along the track.
     *
     * @param top    the thumb's top
     * @param height the thumb's height
     */
    record Thumb(int top, int height) {
    }

    /**
     * @param step how far one wheel notch moves the offset
     */
    ScrollBar(int step) {
        this(step, false);
    }

    private ScrollBar(int step, boolean tails) {
        this.step = step;
        this.tails = tails;
        this.following = tails;
    }

    /**
     * A bar that stands at the content's end and follows it as it grows, until the wheel or a drag moves it off,
     * as a run's progress lines show their latest.
     *
     * @param step how far one wheel notch moves the offset
     * @return the bar
     */
    static ScrollBar tailing(int step) {
        return new ScrollBar(step, true);
    }

    /**
     * @return how far the view stands from the content's start
     */
    int offset() {
        return offset;
    }

    /**
     * @return the view's length as last measured
     */
    int view() {
        return view;
    }

    /**
     * @return the farthest the offset can stand, as last measured
     */
    int maximum() {
        return maximum;
    }

    /**
     * Takes the area's sizes this frame and clamps the offset to them.
     *
     * @param content the content's length
     * @param view    the view's length
     */
    void measure(int content, int view) {
        this.view = view;
        maximum = Math.max(0, content - view);
        offset = following ? maximum : Math.clamp(offset, 0, maximum);
    }

    /**
     * @param amount the wheel's turn, positive away from the player, which moves toward the content's start
     */
    void wheel(double amount) {
        scrollTo(offset - (int) Math.signum(amount) * step);
    }

    /**
     * @param wanted the offset to stand at, clamped to the content
     */
    void scrollTo(int wanted) {
        offset = Math.clamp(wanted, 0, maximum);
        following = tails && offset == maximum;
    }

    /**
     * Stands the view so a stretch of the content sits in its middle, the clamp stopping it at the content's end.
     * Decision shift-follow-centers-the-target-in-its-scrolling-view.
     *
     * @param top    the stretch's top inside the content
     * @param height the stretch's height
     */
    void center(int top, int height) {
        scrollTo(top + (int) (height * HALF) - (int) (view * HALF));
    }

    /**
     * Returns the view to the content's start, or a tailing bar to its end.
     */
    void reset() {
        offset = 0;
        following = tails;
    }

    /**
     * @param trackTop    the track's top
     * @param trackHeight the track's height
     * @return the thumb along that track
     */
    Thumb thumb(int trackTop, int trackHeight) {
        if (maximum == 0) {
            return new Thumb(trackTop, trackHeight);
        }
        int height = Math.max(MIN_THUMB, (int) ((long) trackHeight * view / (maximum + view)));
        return new Thumb(trackTop + (int) ((long) (trackHeight - height) * offset / maximum), height);
    }

    /**
     * Moves the offset so the thumb's middle stands at the cursor.
     *
     * @param y           the cursor's y
     * @param trackTop    the track's top
     * @param trackHeight the track's height
     */
    void drag(double y, int trackTop, int trackHeight) {
        int thumbHeight = thumb(trackTop, trackHeight).height();
        int travel = trackHeight - thumbHeight;
        if (travel <= 0) {
            return;
        }
        double share = (y - trackTop - thumbHeight * HALF) / travel;
        scrollTo((int) Math.round(share * maximum));
    }

    /**
     * Draws the track and thumb in the strip at the area's right edge and takes a press or drag there.
     *
     * @param graphics the frame's graphics
     * @param targets  the frame's click areas
     * @param right    the area's right edge
     * @param top      the area's top
     * @param bottom   the area's bottom
     */
    void draw(GuiGraphicsExtractor graphics, ClickTargets targets, int right, int top, int bottom) {
        int left = right - WIDTH;
        int height = bottom - top;
        Thumb thumb = thumb(top, height);
        graphics.fill(left, top, right, bottom, TRACK_COLOR);
        graphics.fill(left, thumb.top(), right, thumb.top() + thumb.height(), THUMB_COLOR);
        targets.addDrag(left, top, WIDTH, height, y -> drag(y, top, height));
    }

    /**
     * Measures an area scrolled in pixels from where its content ended this frame, then draws its bar.
     *
     * @param graphics   the frame's graphics
     * @param targets    the frame's click areas
     * @param contentEnd the y the content ended at, drawn scrolled by the offset
     * @param right      the area's right edge
     * @param top        the area's top
     * @param bottom     the area's bottom
     */
    void measureAndDraw(GuiGraphicsExtractor graphics, ClickTargets targets, int contentEnd, int right, int top,
                        int bottom) {
        measure(contentEnd + offset - top, bottom - top);
        draw(graphics, targets, right, top, bottom);
    }
}
