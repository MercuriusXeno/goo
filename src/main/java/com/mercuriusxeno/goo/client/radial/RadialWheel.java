package com.mercuriusxeno.goo.client.radial;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * The glove's one radial wheel as pure layout and state: one ring of petals
 * from the hub to the rim. At rest it holds one petal per type, each a full
 * type arc. Hovering a type (or scrolling to it) opens it with no click: its
 * petal is replaced in the same ring by its ability petals, and the other
 * types shrink to make room. The cursor in the hub returns the wheel to rest.
 * decision abilities-replace-the-hovered-type
 * decision ability-petals-take-a-type-arc-to-a-floor
 *
 * <p>Angles run clockwise from the top, the way the petal masks are drawn.
 */
public final class RadialWheel {

    /** The wheel's diameter as a fraction of the smaller screen dimension. */
    static final double SCREEN_FRACTION = 0.95;
    /** The hub's radius as a fraction of the wheel's: the cursor inside it returns the wheel to rest. */
    static final double HUB_FRACTION = 0.2;
    /** No type open, or no ability hovered. */
    static final int NONE = -1;
    /** The narrowest arc a shrunken type petal keeps while another type is open. */
    static final double TYPE_FLOOR = Math.toRadians(10.0);

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double HALF = 0.5;
    /** A scroll up steps the open type one petal counterclockwise. */
    private static final int STEP_BACK = -1;

    private final int typeCount;
    private final IntUnaryOperator abilityCount;
    private int selectedType = NONE;
    private int hoveredAbility = NONE;

    /**
     * Creates the wheel at rest.
     *
     * @param typeCount    the number of type petals
     * @param abilityCount the number of abilities a type index opens to
     */
    public RadialWheel(int typeCount, IntUnaryOperator abilityCount) {
        this.typeCount = typeCount;
        this.abilityCount = abilityCount;
    }

    /**
     * The wheel's outer radius for a screen: 95% of the smaller dimension, halved.
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
     * The arc each type petal spans at rest.
     *
     * @return the arc in radians
     */
    double typeArc() {
        return TWO_PI / typeCount;
    }

    /**
     * The ring's petals in clockwise order from the top: one per type at
     * rest, or with the open type's petal replaced by its ability petals.
     * The renderer and the cursor both read this one list.
     *
     * @return the petals, their arcs summing to a full turn
     */
    List<PetalArc> layout() {
        int open = isOpen() ? abilityCount.applyAsInt(selectedType) : 0;
        Arcs arcs = open > 0 ? arcsWithOpenType(open) : new Arcs(typeArc(), 0.0);
        List<PetalArc> petals = new ArrayList<>(typeCount + open);
        for (int type = 0; type < typeCount; type++) {
            if (type == selectedType && open > 0) {
                addAbilityPetals(petals, type, open, arcs.ability());
            } else {
                petals.add(new PetalArc(type, NONE, endOf(petals), arcs.type()));
            }
        }
        return petals;
    }

    private static void addAbilityPetals(List<PetalArc> petals, int type, int abilities, double arc) {
        for (int ability = 0; ability < abilities; ability++) {
            petals.add(new PetalArc(type, ability, endOf(petals), arc));
        }
    }

    private static double endOf(List<PetalArc> petals) {
        if (petals.isEmpty()) {
            return 0.0;
        }
        PetalArc last = petals.getLast();
        return last.start() + last.arc();
    }

    /**
     * Sizes the petals around an open type: each ability takes a full type
     * arc while every other type can keep the floor; past that the other
     * types hold the floor and the abilities split the remainder evenly.
     * decision ability-petals-take-a-type-arc-to-a-floor
     *
     * @param abilities the open type's ability count, above zero
     * @return the arc of each other type and of each ability
     */
    private Arcs arcsWithOpenType(int abilities) {
        int others = typeCount - 1;
        if (others == 0) {
            return new Arcs(0.0, TWO_PI / abilities);
        }
        double floor = Math.min(TYPE_FLOOR, typeArc());
        double othersAtFullAbilities = (TWO_PI - abilities * typeArc()) / others;
        if (othersAtFullAbilities >= floor) {
            return new Arcs(othersAtFullAbilities, typeArc());
        }
        return new Arcs(floor, (TWO_PI - others * floor) / abilities);
    }

    /**
     * One layout's two petal sizes.
     *
     * @param type    the arc of each type petal
     * @param ability the arc of each ability petal of the open type
     */
    private record Arcs(double type, double ability) {
    }

    /**
     * The petal of a layout an angle falls on.
     *
     * @param petals the layout
     * @param angle  the angle in [0, 2 pi)
     * @return the petal holding the angle
     */
    static PetalArc petalAt(List<PetalArc> petals, double angle) {
        for (PetalArc petal : petals) {
            if (angle < petal.start() + petal.arc()) {
                return petal;
            }
        }
        return petals.getLast();
    }

    /**
     * Moves the cursor: the hub returns the wheel to rest, an ability petal
     * of the open type hovers it, and another type's petal opens that type.
     * decision abilities-replace-the-hovered-type
     *
     * @param dx          the cursor's x offset from the center
     * @param dy          the cursor's y offset from the center, down positive
     * @param outerRadius the wheel's outer radius
     */
    public void moveCursor(double dx, double dy, double outerRadius) {
        if (typeCount == 0) {
            return;
        }
        if (Math.hypot(dx, dy) < HUB_FRACTION * outerRadius) {
            selectedType = NONE;
            hoveredAbility = NONE;
            return;
        }
        PetalArc petal = petalAt(layout(), angleOf(dx, dy));
        if (petal.isAbility()) {
            hoveredAbility = petal.ability();
        } else {
            selectedType = petal.type();
            hoveredAbility = NONE;
        }
    }

    /**
     * Steps the open type with the scroll wheel, for a controller.
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
     * The pick the glove menu key's release makes: the hovered ability of
     * the open type selects it, anywhere else cancels. Either way the wheel closes.
     * decision radial-selects-on-g-release
     *
     * @return the pick's outcome
     */
    public Outcome click() {
        return isOpen() && hoveredAbility != NONE
                ? new Outcome(selectedType, hoveredAbility)
                : Outcome.CANCEL;
    }

    /**
     * Whether a type is open, its abilities in place of its petal.
     *
     * @return true while a type is open
     */
    public boolean isOpen() {
        return selectedType != NONE;
    }

    /**
     * The open type.
     *
     * @return the type index, or {@link #NONE} at rest
     */
    public int selectedType() {
        return selectedType;
    }

    /**
     * The hovered ability of the open type.
     *
     * @return the ability index, or {@link #NONE}
     */
    public int hoveredAbility() {
        return hoveredAbility;
    }

    /**
     * One petal of the ring: a type's own petal, or one ability petal of the open type.
     *
     * @param type    the type index the petal belongs to
     * @param ability the ability index, or {@link #NONE} for the type's own petal
     * @param start   the petal's start angle, clockwise from the top
     * @param arc     the petal's span in radians
     */
    record PetalArc(int type, int ability, double start, double arc) {

        /**
         * Whether the petal is an ability of the open type.
         *
         * @return true for an ability petal
         */
        boolean isAbility() {
            return ability != NONE;
        }

        /**
         * The angle at the petal's middle.
         *
         * @return the angle in radians
         */
        double center() {
            return start + arc * HALF;
        }
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
