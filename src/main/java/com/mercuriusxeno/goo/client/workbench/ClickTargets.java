package com.mercuriusxeno.goo.client.workbench;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleConsumer;

/**
 * The areas a manager pane drew this frame that answer a click or a hover, rebuilt each frame: the area drawn last
 * wins, so a link drawn over its expression or a dialog drawn over its pane takes the click
 * (decision goo-workbench-opens-the-in-game-manager-screen).
 */
final class ClickTargets {
    private final List<Target> targets = new ArrayList<>();
    private @Nullable Clip clip;
    private @Nullable DoubleConsumer grabbed;
    private double pressedY;

    private record Target(int left, int top, int right, int bottom, Runnable action, @Nullable String hoverKey) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    private record Clip(int left, int top, int right, int bottom) {
    }

    /**
     * Forgets every area, as a new frame begins.
     */
    void clear() {
        targets.clear();
        clip = null;
    }

    /**
     * Areas added from now on answer only inside the rectangle, as a scrolled list draws only inside it.
     *
     * @param left   the clip's left edge
     * @param top    the clip's top edge
     * @param right  the clip's right edge
     * @param bottom the clip's bottom edge
     */
    void clip(int left, int top, int right, int bottom) {
        clip = new Clip(left, top, right, bottom);
    }

    /**
     * Areas added from now on answer wherever they stand.
     */
    void unclip() {
        clip = null;
    }

    /**
     * @param left   the area's left edge
     * @param top    the area's top edge
     * @param width  its width
     * @param height its height
     * @param action what a click on it does
     */
    void add(int left, int top, int width, int height, Runnable action) {
        add(left, top, width, height, action, null);
    }

    /**
     * @param left     the area's left edge
     * @param top      the area's top edge
     * @param width    its width
     * @param height   its height
     * @param action   what a click on it does
     * @param hoverKey what hovering it names, null for nothing
     */
    void add(int left, int top, int width, int height, Runnable action, @Nullable String hoverKey) {
        int right = left + width;
        int bottom = top + height;
        if (clip != null) {
            left = Math.max(left, clip.left());
            top = Math.max(top, clip.top());
            right = Math.min(right, clip.right());
            bottom = Math.min(bottom, clip.bottom());
            if (left >= right || top >= bottom) {
                return;
            }
        }
        targets.add(new Target(left, top, right, bottom, action, hoverKey));
    }

    /**
     * An area a press grabs, such as a scroll bar's track: the press and every drag until the release hand their y
     * to the area, wherever the cursor stands.
     *
     * @param left   the area's left edge
     * @param top    the area's top edge
     * @param width  its width
     * @param height its height
     * @param drag   what the press's and each drag's y moves
     */
    void addDrag(int left, int top, int width, int height, DoubleConsumer drag) {
        add(left, top, width, height, () -> {
            grabbed = drag;
            drag.accept(pressedY);
        });
    }

    /**
     * @param y the cursor's y as it drags
     * @return whether a grabbed area took the drag
     */
    boolean drag(double y) {
        if (grabbed == null) {
            return false;
        }
        grabbed.accept(y);
        return true;
    }

    /**
     * Lets go of the grabbed area as the button is released.
     *
     * @return whether an area was grabbed
     */
    boolean release() {
        boolean held = grabbed != null;
        grabbed = null;
        return held;
    }

    /**
     * @param x the click's x
     * @param y the click's y
     * @return whether an area took the click
     */
    boolean click(double x, double y) {
        pressedY = y;
        Optional<Target> hit = topmost(x, y);
        hit.ifPresent(target -> target.action().run());
        return hit.isPresent();
    }

    /**
     * @param x the cursor's x
     * @param y the cursor's y
     * @return what the topmost area under the cursor names, empty where it names nothing
     */
    Optional<String> hovered(double x, double y) {
        return topmost(x, y).map(Target::hoverKey);
    }

    private Optional<Target> topmost(double x, double y) {
        for (int index = targets.size() - 1; index >= 0; index--) {
            if (targets.get(index).contains(x, y)) {
                return Optional.of(targets.get(index));
            }
        }
        return Optional.empty();
    }
}
