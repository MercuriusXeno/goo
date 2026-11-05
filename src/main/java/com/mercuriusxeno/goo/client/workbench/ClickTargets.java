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
    // The grab areas that name what they drag, which no hover reads.
    private final List<NamedGrab> namedGrabs = new ArrayList<>();
    private @Nullable Clip clip;
    private @Nullable PointerGrab grabbed;
    private double pressedX;
    private double pressedY;
    private boolean shiftHeld;
    private boolean controlHeld;

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

    private record NamedGrab(int left, int top, int right, int bottom, String name) {
        boolean contains(double x, double y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    /**
     * Forgets every area, as a new frame begins.
     */
    void clear() {
        targets.clear();
        namedGrabs.clear();
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
     * Hands Ctrl's state to the held grab, so a rail drag reads it live through the hold and its state at the release
     * decides a copy from a move (decision ctrl-during-a-rail-drag-shows-a-green-plus-and-drops-a-copy).
     *
     * @param held whether Ctrl is down now, as the screen reads it each frame and as the button comes up
     */
    void holdControl(boolean held) {
        controlHeld = held;
        if (grabbed != null) {
            grabbed.holdControl(held);
        }
    }

    /**
     * @return whether the held grab would drop a copy, which the frame's cursor shows
     */
    boolean copying() {
        return grabbed != null && grabbed.copies();
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
     * An area that modifies its thing on a plain left click and goes to its ref on a shift + left click, as a ref drawn
     * as a dropdown button does.
     *
     * @param left     the area's left edge
     * @param top      the area's top edge
     * @param width    its width
     * @param height   its height
     * @param gestures what a plain and a shift + left click on it do
     * @param hoverKey what hovering it names, null for nothing
     */
    void addLink(int left, int top, int width, int height, LinkGestures gestures, @Nullable String hoverKey) {
        addTarget(left, top, width, height, new Actions(gestures.modify(), gestures.follow(), false), hoverKey);
    }

    /**
     * What a link area does on each left click.
     *
     * @param modify what a plain left click does
     * @param follow what a shift + left click does
     */
    record LinkGestures(Runnable modify, Runnable follow) {
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
     * Whether an overlay stands over the point: something laid after the frame's first shield, an open list's choice
     * or its own shield, covers it. The first shield itself spans the whole screen to close the list on a press
     * elsewhere, so a point it alone covers stands under no overlay.
     * Decision open-dropdown-intercepts-clicks-before-the-pane-behind-it.
     *
     * @param x the press's x
     * @param y the press's y
     * @return whether an overlay takes a press there before any widget behind it
     */
    boolean overlayAt(double x, double y) {
        return targets.stream().dropWhile(target -> !target.actions().shield()).skip(1)
                .anyMatch(target -> target.contains(x, y));
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
     * What a grabbed area does as the cursor drags it and lets it go.
     */
    interface PointerGrab {
        /**
         * @param x the cursor's x, at the press and at each drag
         * @param y the cursor's y
         */
        void moved(double x, double y);

        /**
         * The button came up.
         */
        void released();

        /**
         * @param held whether Ctrl is down, at the press and at each change until the release; a grab that never
         *             copies ignores it
         */
        default void holdControl(boolean held) {
        }

        /**
         * @return whether the release would drop a copy rather than move the thing held
         */
        default boolean copies() {
            return false;
        }
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
        addGrab(left, top, width, height, new PointerGrab() {
            @Override
            public void moved(double x, double y) {
                drag.accept(y);
            }

            @Override
            public void released() {
                // A track lets go with nothing to finish.
            }
        });
    }

    /**
     * An area a press grabs, as a term's drag rail: the press and every drag until the release hand the cursor's
     * place to the grab, and the release tells it the button came up.
     *
     * @param left   the area's left edge
     * @param top    the area's top edge
     * @param width  its width
     * @param height its height
     * @param grab   what the press, each drag and the release move
     */
    void addGrab(int left, int top, int width, int height, PointerGrab grab) {
        add(left, top, width, height, () -> {
            grabbed = grab;
            grab.holdControl(controlHeld);
            grab.moved(pressedX, pressedY);
        });
    }

    /**
     * A grab area naming what it drags, as a term's rail names its term, while hovering it names nothing (decision
     * drag-rails-hover-nothing).
     *
     * @param left   the area's left edge
     * @param top    the area's top edge
     * @param width  its width
     * @param height its height
     * @param grab   what the press, each drag and the release move
     * @param name   what it drags
     */
    void addGrab(int left, int top, int width, int height, PointerGrab grab, String name) {
        addGrab(left, top, width, height, grab);
        namedGrabs.add(new NamedGrab(left, top, left + width, top + height, name));
    }

    /**
     * @param x the cursor's x
     * @param y the cursor's y
     * @return what the topmost named grab area under the cursor drags, empty where none stands
     */
    Optional<String> grabNamed(double x, double y) {
        for (int index = namedGrabs.size() - 1; index >= 0; index--) {
            if (namedGrabs.get(index).contains(x, y)) {
                return Optional.of(namedGrabs.get(index).name());
            }
        }
        return Optional.empty();
    }

    /**
     * @param x the cursor's x as it drags
     * @param y the cursor's y as it drags
     * @return whether a grabbed area took the drag
     */
    boolean drag(double x, double y) {
        if (grabbed == null) {
            return false;
        }
        grabbed.moved(x, y);
        return true;
    }

    /**
     * Lets go of the grabbed area as the button is released.
     *
     * @return whether an area was grabbed
     */
    boolean release() {
        PointerGrab held = grabbed;
        grabbed = null;
        if (held != null) {
            held.released();
        }
        return held != null;
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
        pressedX = x;
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
     * @param x the cursor's x
     * @param y the cursor's y
     * @return whether a plain left click here would run an area's action, without running it
     */
    boolean takesClick(double x, double y) {
        for (int index = targets.size() - 1; index >= 0; index--) {
            Target target = targets.get(index);
            if (target.contains(x, y) && target.actions().forGesture(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param x the cursor's x
     * @param y the cursor's y
     * @return whether a shift + left click here would follow a link: the topmost area taking that click holds a
     *     follow action and is no shield
     */
    boolean followAt(double x, double y) {
        for (int index = targets.size() - 1; index >= 0; index--) {
            Target target = targets.get(index);
            if (target.contains(x, y)
                    && target.actions().forGesture(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT) != null) {
                return !target.actions().shield() && target.actions().follow() != null;
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
        return hoveredArea(x, y).map(HoveredArea::hoverKey);
    }

    /**
     * What a hovered area names and where it stands.
     *
     * @param hoverKey what hovering it names
     * @param bounds   the area, as clipped when it was added
     */
    record HoveredArea(String hoverKey, GroupsPane.Bounds bounds) {
    }

    /**
     * @param x the cursor's x
     * @param y the cursor's y
     * @return what the topmost area under the cursor names, with its bounds, empty where it names nothing
     */
    Optional<HoveredArea> hoveredArea(double x, double y) {
        for (int index = targets.size() - 1; index >= 0; index--) {
            Target target = targets.get(index);
            if (target.contains(x, y)) {
                return Optional.ofNullable(target.hoverKey()).map(key -> new HoveredArea(key,
                        new GroupsPane.Bounds(target.left(), target.top(), target.right(), target.bottom())));
            }
        }
        return Optional.empty();
    }
}
