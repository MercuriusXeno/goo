package com.mercuriusxeno.goo.client.workbench;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleConsumer;

/**
 * The areas a manager pane drew this frame that answer a click or a hover, rebuilt each frame: the area drawn last
 * wins, so a link drawn over its expression or a dialog drawn over its pane takes the click
 * (decision goo-workbench-opens-the-in-game-manager-screen). An area answers each gesture apart: a plain left click
 * modifies the thing in place and shift + left click goes to the ref, so a ref drawn over its expression takes the
 * shift + left click and lets the plain left click reach the view beneath it; a right click reaches no link, and a
 * shield, as a dialog's backdrop, takes every click.
 * Decision shift-left-click-follows-plain-left-modifies.
 */
final class ClickTargets {
    private final List<Target> targets = new ArrayList<>();
    private @Nullable Clip clip;
    private @Nullable DoubleConsumer grabbed;
    private double pressedY;
    private boolean shiftHeld;

    // What an area does on a plain left click and on a shift + left click; a shield takes every click with its
    // modify action.
    private record Actions(@Nullable Runnable modify, @Nullable Runnable follow, boolean shield) {
        private static final Actions NONE = new Actions(null, null, false);

        @Nullable Runnable forGesture(int button, int modifiers) {
            if (shield) {
                return modify;
            }
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return null;
            }
            return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? follow : modify;
        }
    }

    private record Target(int left, int top, int right, int bottom, Actions actions, @Nullable String hoverKey) {
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
     * @param held whether shift is held this frame, as the screen reads it once per frame, so each link draws as one
     *             a click now follows
     */
    void holdShift(boolean held) {
        shiftHeld = held;
    }

    /**
     * @return whether shift is held this frame
     */
    boolean shiftHeld() {
        return shiftHeld;
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
        addTarget(left, top, width, height, new Actions(action, null, false), hoverKey);
    }

    /**
     * An area a shift + left click follows, as a ref goes to what it names; a plain left click passes to the area
     * under it.
     *
     * @param left     the area's left edge
     * @param top      the area's top edge
     * @param width    its width
     * @param height   its height
     * @param follow   what a shift + left click on it does
     * @param hoverKey what hovering it names, null for nothing
     */
    void addFollow(int left, int top, int width, int height, Runnable follow, @Nullable String hoverKey) {
        addTarget(left, top, width, height, new Actions(null, follow, false), hoverKey);
    }

    /**
     * An area taking every click, plain, shifted or right, as a dialog's backdrop stops a click reaching the pane
     * beneath it.
     *
     * @param left   the area's left edge
     * @param top    the area's top edge
     * @param width  its width
     * @param height its height
     * @param action what any click on it does
     */
    void addShield(int left, int top, int width, int height, Runnable action) {
        addTarget(left, top, width, height, new Actions(action, action, true), null);
    }

    /**
     * An area that names what hovering it shows and takes no click, so a click passes to the area under it.
     *
     * @param left     the area's left edge
     * @param top      the area's top edge
     * @param width    its width
     * @param height   its height
     * @param hoverKey what hovering it names
     */
    void addHover(int left, int top, int width, int height, String hoverKey) {
        addTarget(left, top, width, height, Actions.NONE, hoverKey);
    }

    private void addTarget(int left, int top, int width, int height, Actions actions, @Nullable String hoverKey) {
        int clippedLeft = left;
        int clippedTop = top;
        int right = left + width;
        int bottom = top + height;
        if (clip != null) {
            clippedLeft = Math.max(left, clip.left());
            clippedTop = Math.max(top, clip.top());
            right = Math.min(right, clip.right());
            bottom = Math.min(bottom, clip.bottom());
            if (clippedLeft >= right || clippedTop >= bottom) {
                return;
            }
        }
        targets.add(new Target(clippedLeft, clippedTop, right, bottom, actions, hoverKey));
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
     * @param x the left click's x
     * @param y the left click's y
     * @return whether an area took the click
     */
    boolean click(double x, double y) {
        return click(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, 0);
    }

    /**
     * @param x         the click's x
     * @param y         the click's y
     * @param button    the GLFW mouse button pressed
     * @param modifiers the GLFW modifier bits held as it was pressed
     * @return whether an area holding an action for the gesture took the click
     */
    boolean click(double x, double y, int button, int modifiers) {
        pressedY = y;
        for (int index = targets.size() - 1; index >= 0; index--) {
            Target target = targets.get(index);
            Runnable action = target.actions().forGesture(button, modifiers);
            if (action != null && target.contains(x, y)) {
                action.run();
                return true;
            }
        }
        return false;
    }

    /**
     * @return what each area names on hover, in the order the frame laid them, the areas naming nothing left out
     */
    List<String> hoverKeys() {
        return targets.stream().map(Target::hoverKey).filter(Objects::nonNull).toList();
    }

    /**
     * @param x the cursor's x
     * @param y the cursor's y
     * @return what the topmost area under the cursor names, empty where it names nothing
     */
    Optional<String> hovered(double x, double y) {
        for (int index = targets.size() - 1; index >= 0; index--) {
            if (targets.get(index).contains(x, y)) {
                return Optional.ofNullable(targets.get(index).hoverKey());
            }
        }
        return Optional.empty();
    }
}
