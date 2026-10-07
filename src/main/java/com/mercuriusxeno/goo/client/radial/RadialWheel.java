package com.mercuriusxeno.goo.client.radial;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
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
    static final double SCREEN_FRACTION = 0.98;
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
    /** Client ticks the ring follows the cursor after a type opens: a tenth of a second. */
    static final int GRACE_TICKS = 2;
    /**
     * How far out an open type's petal reaches, as a fraction of the wheel's
     * radius, standing as the base its ability petals grow out of.
     */
    static final double TYPE_BASE_LENGTH = 0.6;
    /**
     * How far out the hovered ability petal reaches, as a fraction of the
     * wheel's radius: the edge of the screen.
     */
    static final double HOVER_REACH = 1.0 / SCREEN_FRACTION;
    /** Client ticks the hovered ability petal takes to grow to the screen's edge, or back. */
    static final int LIFT_TICKS = 3;
    /** The smallest openness a fanned card draws at. */
    private static final double VISIBLE = 1e-6;

    private final int typeCount;
    private final IntUnaryOperator abilityCount;
    private int selectedType = NONE;
    private int hoveredAbility = NONE;
    /** Per type and ability, the ticks its petal has grown toward the screen's edge. */
    private final int[][] liftTicks;
    /** The angle every petal's start is turned by, clockwise; zero at rest. */
    private double rotation;
    /** The cursor's last angle outside the hub, or NaN while it has none. */
    private double cursorAngle = Double.NaN;
    /** Client ticks left in the grace window after a type opened under the cursor. */
    private int graceTicks;
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
        this.liftTicks = new int[typeCount][];
        for (int type = 0; type < typeCount; type++) {
            liftTicks[type] = new int[abilityCount.applyAsInt(type)];
        }
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
        List<PetalArc> petals = new ArrayList<>(petalsOf(targetPose(),
                (type, ability) -> liftTarget(type, ability) > 0 ? HOVER_REACH : 1.0));
        petals.removeIf(this::isOpenBase);
        petals.sort(Comparator.comparingDouble(PetalArc::start));
        return petals;
    }

    /**
     * Whether a petal is the open type's own petal standing as its abilities'
     * base, which the cursor reads through to the abilities over it.
     *
     * @param petal the petal
     * @return true for the base of a type fanned out into abilities
     */
    private boolean isOpenBase(PetalArc petal) {
        return !petal.isAbility() && petal.type() == selectedType && abilityCount.applyAsInt(selectedType) > 0;
    }

    /**
     * The ring's petals as drawn this frame, easing toward {@link #layout()},
     * in draw order: each type's ability cards from the last to the first,
     * then the type's own petal on top, so the cards fan out from behind it.
     * decision petal-moves-animate
     *
     * @param partialTick the fraction of a tick since the last one
     * @return the petals on display, an absent petal left out
     */
    List<PetalArc> displayedLayout(float partialTick) {
        return petalsOf(ease.displayed(partialTick),
                (type, ability) -> 1.0 + (HOVER_REACH - 1.0) * liftOf(type, ability, partialTick));
    }

    /**
     * How far an ability petal of the open type has grown toward the screen's edge.
     * decision abilities-replace-the-hovered-type
     *
     * @param type        the type index
     * @param ability     the ability index
     * @param partialTick the fraction of a tick since the last one
     * @return 0 at the wheel's rim, 1 at the screen's edge
     */
    private double liftOf(int type, int ability, float partialTick) {
        int ticks = liftTicks[type][ability];
        double moving = Integer.signum(liftTarget(type, ability) - ticks) * partialTick;
        return (ticks + moving) / LIFT_TICKS;
    }

    private int liftTarget(int type, int ability) {
        return type == selectedType && ability == hoveredAbility ? LIFT_TICKS : 0;
    }

    /** Advances the petals' ease and the grace window one client tick. */
    public void tick() {
        ease.tick();
        for (int type = 0; type < typeCount; type++) {
            for (int ability = 0; ability < liftTicks[type].length; ability++) {
                liftTicks[type][ability] += Integer.signum(liftTarget(type, ability) - liftTicks[type][ability]);
            }
        }
        if (graceTicks > 0) {
            graceTicks--;
        }
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
     * Whether the ring still follows the cursor after a type opened.
     * decision ring-rotates-to-keep-the-cursor-inside
     *
     * @return true inside the grace window
     */
    boolean isInGrace() {
        return graceTicks > 0;
    }

    /**
     * The target pose: the open type's slot as wide as its abilities and
     * fully open, every other type's slot its shrunken or resting arc and closed.
     *
     * @return the target pose
     */
    private RingEase.Pose targetPose() {
        int open = isOpen() ? abilityCount.applyAsInt(selectedType) : 0;
        Arcs arcs = arcsAround(open);
        int fanned = open > 0 ? selectedType : NONE;
        double[] widths = new double[typeCount];
        double[] openness = new double[typeCount];
        for (int type = 0; type < typeCount; type++) {
            widths[type] = type == fanned ? open * arcs.ability() : arcs.type();
            openness[type] = type == fanned ? 1.0 : 0.0;
        }
        return new RingEase.Pose(rotation, widths, openness);
    }

    /**
     * Lays a pose around the ring from its rotation, each type in its slot:
     * its ability cards, full size, fanned clockwise from stacked at the
     * slot's start as far as the type is open, and its own petal across the
     * slot, shortened toward the base length as far as the type is open, so
     * a fully open type's petal is the base its abilities grow out of.
     * decision petal-moves-animate
     *
     * @param pose       the pose
     * @param cardLength each ability petal's length, by type and ability index
     * @return the petals in draw order
     */
    private List<PetalArc> petalsOf(RingEase.Pose pose, CardLength cardLength) {
        List<PetalArc> petals = new ArrayList<>();
        double start = pose.rotation();
        for (int type = 0; type < typeCount; type++) {
            double openness = pose.openness()[type];
            double width = pose.widths()[type];
            double length = 1.0 - (1.0 - TYPE_BASE_LENGTH) * openness;
            PetalMask.Petal base = new PetalMask.Petal(start, width, HUB_FRACTION, length);
            addCards(petals, type, new Slot(start, openness, base), cardLength);
            if (width > 0) {
                petals.add(new PetalArc(type, NONE, start, width, length, null, openness));
            }
            start += width;
        }
        return petals;
    }

    private void addCards(List<PetalArc> petals, int type, Slot slot, CardLength cardLength) {
        int abilities = abilityCount.applyAsInt(type);
        if (abilities == 0 || slot.openness() <= VISIBLE) {
            return;
        }
        double card = arcsWithOpenType(abilities).ability();
        for (int ability = abilities - 1; ability >= 0; ability--) {
            petals.add(new PetalArc(type, ability, slot.start() + ability * card * slot.openness(), card,
                    cardLength.of(type, ability), slot.base(), slot.openness()));
        }
    }

    /**
     * A type's slot as its cards read it.
     *
     * @param start    the slot's start angle
     * @param openness how far the type is open
     * @param base     the type's own petal, which its cards start out from
     */
    private record Slot(double start, double openness, PetalMask.Petal base) {
    }

    /**
     * The petal sizes with an open type's abilities, or at rest when none fan out.
     *
     * @param abilities the open type's ability count, zero at rest
     * @return the arc of each type and of each ability
     */
    private Arcs arcsAround(int abilities) {
        return abilities > 0 ? arcsWithOpenType(abilities) : new Arcs(typeArc(), 0.0);
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
        PetalArc nearest = petals.getFirst();
        double nearestOff = Double.MAX_VALUE;
        for (PetalArc petal : petals) {
            double offset = wrap(angle - petal.start());
            if (offset < petal.arc()) {
                return petal;
            }
            if (offset - petal.arc() < nearestOff) {
                nearestOff = offset - petal.arc();
                nearest = petal;
            }
        }
        return nearest;
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
            returnToRest();
            return;
        }
        cursorAngle = angleOf(dx, dy);
        if (isOpen() && isInGrace()) {
            centerOnCursor();
            return;
        }
        landOn(petalAt(layout(), cursorAngle));
    }

    /**
     * The cursor on a petal: an ability of the open type hovers it, another type's petal opens that type.
     *
     * @param petal the petal under the cursor
     */
    private void landOn(PetalArc petal) {
        if (petal.isAbility()) {
            hoveredAbility = petal.ability();
        } else if (petal.type() != selectedType) {
            openType(petal.type());
        }
    }

    /** Closes the open type and turns the ring back to rest, the cursor in the hub. */
    private void returnToRest() {
        boolean wasOpen = isOpen();
        selectedType = NONE;
        hoveredAbility = NONE;
        rotation = 0.0;
        cursorAngle = Double.NaN;
        graceTicks = 0;
        if (wasOpen) {
            ease.retarget(targetPose());
        }
    }

    /**
     * Opens a type and turns the ring the least it must to put the cursor
     * inside the type's abilities, hovers the ability it lands on and turns
     * that ability's center onto the cursor, opening the grace window. With
     * no cursor angle the ring rests unturned.
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
            rotation += turnToCursor(petal);
            graceTicks = GRACE_TICKS;
        }
        ease.retarget(targetPose());
    }

    /**
     * Turns the whole ring, picture and target alike, so the hovered petal's
     * center sits on the cursor: the grace window's follow.
     * decision ring-rotates-to-keep-the-cursor-inside
     */
    private void centerOnCursor() {
        PetalArc anchor = layout().stream()
                .filter(petal -> petal.type() == selectedType && petal.ability() == hoveredAbility)
                .findFirst().orElse(null);
        if (anchor == null) {
            return;
        }
        double turn = turnToCursor(anchor);
        rotation += turn;
        ease.turnBy(turn);
    }

    /**
     * The shortest turn that brings a petal's center onto the cursor.
     *
     * @param petal the petal
     * @return the turn in radians, within half a turn either way
     */
    private double turnToCursor(PetalArc petal) {
        return Math.IEEEremainder(cursorAngle - petal.center(), TWO_PI);
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
     * Steps the hovered ability with the scroll wheel, through every ability
     * of every type in list order: past either end of the open type's
     * abilities it opens the next or previous type with abilities, wrapping
     * around the list, so the radial works on a controller. The ring stays
     * unturned, since a controller has no cursor for it to follow.
     * decision abilities-replace-the-hovered-type
     *
     * @param scrollY the scroll amount; down steps forward
     */
    public void scroll(double scrollY) {
        if (scrollY == 0) {
            return;
        }
        int step = scrollY < 0 ? 1 : STEP_BACK;
        if (!isOpen() || !stepWithinOpenType(step)) {
            openNextTypeWithAbilities(step);
        }
    }

    /**
     * Opens the next or previous type in list order that has abilities, on
     * its first or last, wrapping around the list; from rest, the first or last.
     *
     * @param step 1 forward, -1 back
     */
    private void openNextTypeWithAbilities(int step) {
        int from = isOpen() ? selectedType : step > 0 ? NONE : typeCount;
        for (int tried = 1; tried <= typeCount; tried++) {
            int type = Math.floorMod(from + step * tried, typeCount);
            int abilities = abilityCount.applyAsInt(type);
            if (abilities > 0) {
                openUnturned(type, step > 0 ? 0 : abilities - 1);
                return;
            }
        }
    }

    /**
     * Steps the hover within the open type's abilities: onto its first or
     * last when none is hovered, else to the next one either way.
     *
     * @param step 1 forward, -1 back
     * @return true when the open type held the step, false past either end
     */
    private boolean stepWithinOpenType(int step) {
        int abilities = abilityCount.applyAsInt(selectedType);
        int next = hoveredAbility == NONE ? (step > 0 ? 0 : abilities - 1) : hoveredAbility + step;
        if (next < 0 || next >= abilities) {
            return false;
        }
        hoveredAbility = next;
        return true;
    }

    /**
     * Opens a type with one of its abilities hovered and the ring unturned.
     *
     * @param type    the type to open
     * @param ability the ability to hover
     */
    private void openUnturned(int type, int ability) {
        boolean changed = type != selectedType || rotation != 0.0;
        selectedType = type;
        hoveredAbility = ability;
        rotation = 0.0;
        graceTicks = 0;
        if (changed) {
            ease.retarget(targetPose());
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

    /** An ability petal's length as a fraction of the wheel's radius, by its type and ability. */
    @FunctionalInterface
    private interface CardLength {
        /**
         * The petal's length.
         *
         * @param type    the type index
         * @param ability the ability index
         * @return the length
         */
        double of(int type, int ability);
    }

    /**
     * One petal of the ring: a type's own petal, or one ability petal of the open type.
     *
     * @param type    the type index the petal belongs to
     * @param ability the ability index, or {@link #NONE} for the type's own petal
     * @param start   the petal's start angle, clockwise from the top
     * @param arc     the petal's span in radians
     * @param length  the petal's outer radius as a fraction of the wheel's, short of 1 while it recedes
     * @param root    the type petal an ability petal starts out from, or null for a type petal,
     *                which starts at the hub
     * @param openness how far the petal's type is open, 0 closed to 1 fanned, which the
     *                 ability petal's content slides in by
     */
    record PetalArc(int type, int ability, double start, double arc, double length,
                    PetalMask.@Nullable Petal root, double openness) {

        /**
         * The petal's shape: from the hub for a type petal, from exactly where
         * its type petal ends for an ability petal.
         * decision petal-moves-animate
         *
         * @return the shape
         */
        PetalMask.Petal shape() {
            return new PetalMask.Petal(start, arc, root == null ? HUB_FRACTION : root.outer(), length, root);
        }


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
