package com.mercuriusxeno.goo.client.radial;

import java.util.function.IntUnaryOperator;

/**
 * The glove's one radial wheel as pure layout and state: one wedge per type
 * through an inner and an outer ring. Hovering a type in the inner ring (or
 * scrolling to it) selects it with no click; its wedge recedes to the inner
 * ring and its abilities fan out in the outer ring, each about a type
 * wedge's arc, centered on the type's angle. The cursor past either end of
 * the fan hovers nothing and the fan stays open; the cursor in the hub
 * collapses the fan to types (decision type-recedes-and-abilities-fan-out).
 *
 * <p>Angles run clockwise from the top, the way the wedge masks are drawn.
 */
public final class RadialWheel {

    /** The wheel's diameter as a fraction of the smaller screen dimension. */
    static final double SCREEN_FRACTION = 0.6;
    /** The hub's radius as a fraction of the wheel's: the cursor inside it collapses the fan. */
    static final double HUB_FRACTION = 0.2;
    /** Where the inner ring meets the outer ring, as a fraction of the wheel's radius. */
    static final double RING_FRACTION = 0.62;
    /** No type selected, or no ability hovered. */
    static final int NONE = -1;

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double HALF = 0.5;
    /** A scroll up steps the selection one type counterclockwise. */
    private static final int STEP_BACK = -1;

    private final int typeCount;
    private final IntUnaryOperator abilityCount;
    private int selectedType = NONE;
    private int hoveredAbility = NONE;

    /**
     * Creates the wheel in its types state.
     *
     * @param typeCount    the number of type wedges
     * @param abilityCount the number of abilities a type index fans out
     */
    public RadialWheel(int typeCount, IntUnaryOperator abilityCount) {
        this.typeCount = typeCount;
        this.abilityCount = abilityCount;
    }

    /**
     * The wheel's outer radius for a screen: 60% of the smaller dimension, halved.
     *
     * @param width  the screen width
     * @param height the screen height
     * @return the outer radius
     */
    public static double outerRadius(int width, int height) {
        return Math.min(width, height) * SCREEN_FRACTION * HALF;
    }

    /**
     * The clockwise angle from the top of a cursor offset from the center.
     *
     * @param dx the cursor's x offset
     * @param dy the cursor's y offset, down positive
     * @return the angle in [0, 2 pi)
     */
    static double angleOf(double dx, double dy) {
        return wrap(Math.atan2(dx, -dy));
    }

    private static double wrap(double angle) {
        double wrapped = angle % TWO_PI;
        return wrapped < 0 ? wrapped + TWO_PI : wrapped;
    }

    /**
     * The arc each type wedge spans.
     *
     * @return the arc in radians
     */
    double typeArc() {
        return TWO_PI / typeCount;
    }

    /**
     * The angle at the middle of a type's wedge.
     *
     * @param type the type index
     * @return the angle in radians
     */
    double typeCenter(int type) {
        return (type + HALF) * typeArc();
    }

    /**
     * The type whose wedge holds an angle.
     *
     * @param angle the angle in [0, 2 pi)
     * @return the type index
     */
    int typeAt(double angle) {
        return (int) (angle / typeArc()) % typeCount;
    }

    /**
     * The arc each ability wedge of a type's fan spans: a type wedge's
     * arc, narrowed only when the fan would wrap past a full turn.
     *
     * @param type the type index
     * @return the arc in radians
     */
    double fanArc(int type) {
        int count = abilityCount.applyAsInt(type);
        return count <= 0 ? typeArc() : Math.min(typeArc(), TWO_PI / count);
    }

    /**
     * The angle the first ability wedge of a type's fan starts at, so the
     * fan centers on the type's angle.
     *
     * @param type the type index
     * @return the angle in radians, before wrapping
     */
    double fanStart(int type) {
        return typeCenter(type) - abilityCount.applyAsInt(type) * fanArc(type) * HALF;
    }

    /**
     * The ability of the selected type's fan an angle falls on.
     *
     * @param angle the angle in [0, 2 pi)
     * @return the ability index, or {@link #NONE} past either end of the fan
     */
    int abilityAt(double angle) {
        double offset = wrap(angle - fanStart(selectedType));
        int index = (int) (offset / fanArc(selectedType));
        return index < abilityCount.applyAsInt(selectedType) ? index : NONE;
    }

    /**
     * Moves the cursor: the hub collapses the fan, the inner ring selects
     * the type under it, and the outer ring hovers the fan's ability.
     *
     * @param dx          the cursor's x offset from the center
     * @param dy          the cursor's y offset from the center, down positive
     * @param outerRadius the wheel's outer radius
     */
    public void moveCursor(double dx, double dy, double outerRadius) {
        double distance = Math.hypot(dx, dy);
        double angle = angleOf(dx, dy);
        if (distance < HUB_FRACTION * outerRadius) {
            selectedType = NONE;
            hoveredAbility = NONE;
        } else if (distance < RING_FRACTION * outerRadius) {
            selectedType = typeAt(angle);
            hoveredAbility = NONE;
        } else {
            hoveredAbility = selectedType == NONE ? NONE : abilityAt(angle);
        }
    }

    /**
     * Steps the selected type with the scroll wheel, for a controller.
     *
     * @param scrollY the scroll amount; down steps clockwise
     */
    public void scroll(double scrollY) {
        if (scrollY == 0 || typeCount == 0) {
            return;
        }
        boolean clockwise = scrollY < 0;
        if (selectedType == NONE) {
            selectedType = clockwise ? 0 : typeCount - 1;
        } else {
            selectedType = Math.floorMod(selectedType + (clockwise ? 1 : STEP_BACK), typeCount);
        }
        hoveredAbility = NONE;
    }

    /**
     * A left click: an ability wedge selects it, anywhere else cancels.
     * Either way the wheel closes.
     *
     * @return the click's outcome
     */
    public Outcome click() {
        return isFanned() && hoveredAbility != NONE
                ? new Outcome(selectedType, hoveredAbility)
                : Outcome.CANCEL;
    }

    /**
     * A right click: closes the wheel with the glove unchanged, in either state.
     *
     * @return the cancel outcome
     */
    public Outcome rightClick() {
        return Outcome.CANCEL;
    }

    /**
     * Whether a type is selected and its abilities fan out.
     *
     * @return true in the fanned state
     */
    public boolean isFanned() {
        return selectedType != NONE;
    }

    /**
     * The selected type.
     *
     * @return the type index, or {@link #NONE} in the types state
     */
    public int selectedType() {
        return selectedType;
    }

    /**
     * The hovered ability of the fan.
     *
     * @return the ability index, or {@link #NONE}
     */
    public int hoveredAbility() {
        return hoveredAbility;
    }

    /**
     * A click's outcome: the type and ability it writes to the glove, or a cancel.
     *
     * @param type    the selected type index, or {@link #NONE} for a cancel
     * @param ability the clicked ability index, or {@link #NONE} for a cancel
     */
    public record Outcome(int type, int ability) {

        /** Closes the wheel with the glove unchanged. */
        public static final Outcome CANCEL = new Outcome(NONE, NONE);

        /**
         * Whether the click writes a selection to the glove.
         *
         * @return true for an ability click
         */
        public boolean selects() {
            return ability != NONE;
        }
    }
}
