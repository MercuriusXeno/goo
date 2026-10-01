package com.mercuriusxeno.goo.client.radial;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * The glove's one radial wheel as pure layout and state: one ring of petals
 * from the hub to the rim. At rest it holds one petal per type, each a full
 * type arc. Hovering a type (or scrolling to it) opens it with no click: its
 * petal is replaced in the same ring by its ability petals, and the other
 * types shrink to make room. The ring turns the least it must to keep the
 * cursor inside the open type's abilities. The cursor in the hub returns the
 * wheel to rest, unturned.
 * decision abilities-replace-the-hovered-type
 * decision ability-petals-take-a-type-arc-to-a-floor
 * decision ring-rotates-to-keep-the-cursor-inside
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
    /** How far inside the open type's abilities the ring turns the cursor, from either edge. */
    static final double CURSOR_MARGIN = Math.toRadians(3.0);
    /** Full turns either way the rotation's solve tries, enough to reach zero from any cursor and span. */
    private static final int TURNS_TRIED = 2;

    private final int typeCount;
    private final IntUnaryOperator abilityCount;
    private int selectedType = NONE;
    private int hoveredAbility = NONE;
    /** The angle every petal's start is turned by, clockwise; zero at rest. */
    private double rotation;
    /** The cursor's last angle outside the hub, or NaN while it has none. */
    private double cursorAngle = Double.NaN;
    /** The picture's ease toward the target layout; the state above is the target. */
    private final RingEase ease;

    /**
     * Creates the wheel at rest.
     *
     * @param typeCount    the number of type petals
     * @param abilityCount the number of abilities a type index opens to
     */
    public RadialWheel(int typeCount, IntUnaryOperator abilityCount) {
        this.typeCount = typeCount;
        this.abilityCount = abilityCount;
        this.ease = new RingEase(targetPose());
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
     * The target ring's petals in clockwise order from the rotation: one per
     * type at rest, or with the open type's petal replaced by its ability
     * petals. The cursor reads this layout, so the wheel's state is settled
     * the instant the target is and only the picture lags.
     * decision petal-moves-animate
     *
     * @return the petals, their arcs summing to a full turn
     */
    List<PetalArc> layout() {
        return petalsOf(targetPose());
    }

    /**
     * The ring's petals as drawn this frame, easing toward {@link #layout()}.
     * decision petal-moves-animate
     *
     * @param partialTick the fraction of a tick since the last one
     * @return the petals on display, an absent petal left out
     */
    List<PetalArc> displayedLayout(float partialTick) {
        return petalsOf(ease.displayed(partialTick));
    }

    /** Advances the petals' ease one client tick. */
    public void tick() {
        ease.tick();
    }

    /**
     * Whether the petals are still moving, so input reads nothing yet.
     * decision mid-animation-input-does-nothing
     *
     * @return true until the ease lands
     */
    public boolean isAnimating() {
        return ease.isRunning();
    }

    /**
     * The target as one arc per slot: each type's own petal, then its
     * abilities, the open type's petal and every closed type's abilities zero.
     *
     * @return the target pose
     */
    private RingEase.Pose targetPose() {
        int open = isOpen() ? abilityCount.applyAsInt(selectedType) : 0;
        Arcs arcs = open > 0 ? arcsWithOpenType(open) : new Arcs(typeArc(), 0.0);
        List<Double> slots = new ArrayList<>();
        for (int type = 0; type < typeCount; type++) {
            boolean opened = type == selectedType && open > 0;
            addTypeSlots(slots, type, opened ? new Arcs(0.0, arcs.ability()) : new Arcs(arcs.type(), 0.0));
        }
        return new RingEase.Pose(rotation, slots.stream().mapToDouble(Double::doubleValue).toArray());
    }

    private void addTypeSlots(List<Double> slots, int type, Arcs arcs) {
        slots.add(arcs.type());
        for (int ability = 0; ability < abilityCount.applyAsInt(type); ability++) {
            slots.add(arcs.ability());
        }
    }

    /**
     * Lays a pose's slots around the ring from its rotation, leaving out each
     * slot whose arc is zero.
     *
     * @param pose the pose
     * @return the petals in clockwise order
     */
    private List<PetalArc> petalsOf(RingEase.Pose pose) {
        List<PetalArc> petals = new ArrayList<>();
        double start = pose.rotation();
        int slot = 0;
        for (int type = 0; type < typeCount; type++) {
            for (int ability = NONE; ability < abilityCount.applyAsInt(type); ability++) {
                double arc = pose.arcs()[slot++];
                if (arc > 0) {
                    petals.add(new PetalArc(type, ability, start, arc));
                    start += arc;
                }
            }
        }
        return petals;
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
     * @param petals the layout, its first petal starting anywhere
     * @param angle  the angle, any turn
     * @return the petal holding the angle
     */
    static PetalArc petalAt(List<PetalArc> petals, double angle) {
        double origin = petals.getFirst().start();
        double offset = wrap(angle - origin);
        for (PetalArc petal : petals) {
            if (offset < petal.start() - origin + petal.arc()) {
                return petal;
            }
        }
        return petals.getLast();
    }

    /**
     * Moves the cursor: the hub returns the wheel to rest, an ability petal
     * of the open type hovers it, and another type's petal opens that type,
     * the ring turning to keep the cursor inside its abilities.
     * decision abilities-replace-the-hovered-type
     * decision ring-rotates-to-keep-the-cursor-inside
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
            boolean wasOpen = isOpen();
            selectedType = NONE;
            hoveredAbility = NONE;
            rotation = 0.0;
            cursorAngle = Double.NaN;
            if (wasOpen) {
                ease.retarget(targetPose());
            }
            return;
        }
        cursorAngle = angleOf(dx, dy);
        PetalArc petal = petalAt(layout(), cursorAngle);
        if (petal.isAbility()) {
            hoveredAbility = petal.ability();
        } else if (petal.type() != selectedType) {
            openType(petal.type());
        }
    }

    /**
     * Opens a type and turns the ring the least it must to put the cursor
     * inside the type's abilities, then hovers the ability under the cursor.
     * With no cursor angle the ring rests unturned.
     * decision ring-rotates-to-keep-the-cursor-inside
     *
     * @param type the type to open
     */
    private void openType(int type) {
        selectedType = type;
        hoveredAbility = NONE;
        rotation = 0.0;
        if (!Double.isNaN(cursorAngle)) {
            rotation = solveRotation(layout(), type, cursorAngle);
            PetalArc petal = petalAt(layout(), cursorAngle);
            hoveredAbility = petal.isAbility() ? petal.ability() : NONE;
        }
        ease.retarget(targetPose());
    }

    /**
     * The rotation nearest zero that puts an angle at least the margin
     * inside a type's span of petals; a span narrower than two margins
     * centers on the angle instead.
     * decision ring-rotates-to-keep-the-cursor-inside
     *
     * @param unturned the layout at rotation zero
     * @param type     the type whose span holds the angle
     * @param angle    the angle the span must hold
     * @return the rotation in radians
     */
    static double solveRotation(List<PetalArc> unturned, int type, double angle) {
        List<PetalArc> span = unturned.stream().filter(petal -> petal.type() == type).toList();
        double spanStart = span.getFirst().start();
        double spanEnd = span.getLast().start() + span.getLast().arc();
        double lowest = angle - spanEnd + CURSOR_MARGIN;
        double highest = angle - spanStart - CURSOR_MARGIN;
        if (lowest > highest) {
            double centered = angle - (spanStart + spanEnd) * HALF;
            return nearestToZero(centered, centered);
        }
        return nearestToZero(lowest, highest);
    }

    /**
     * The value nearest zero in an interval of rotations, any whole turn of it.
     *
     * @param lowest  the interval's low end
     * @param highest the interval's high end
     * @return the rotation nearest zero
     */
    private static double nearestToZero(double lowest, double highest) {
        double best = Double.POSITIVE_INFINITY;
        for (int turn = -TURNS_TRIED; turn <= TURNS_TRIED; turn++) {
            double nearest = Math.max(lowest + turn * TWO_PI, Math.min(highest + turn * TWO_PI, 0.0));
            if (Math.abs(nearest) < Math.abs(best)) {
                best = nearest;
            }
        }
        return best;
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
            openType(clockwise ? 0 : typeCount - 1);
        } else {
            openType(Math.floorMod(selectedType + (clockwise ? 1 : STEP_BACK), typeCount));
        }
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
     * The angle every petal is turned by.
     *
     * @return the rotation in radians, clockwise; zero at rest
     */
    double rotation() {
        return rotation;
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
