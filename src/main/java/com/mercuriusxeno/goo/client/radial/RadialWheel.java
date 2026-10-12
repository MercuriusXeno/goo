package com.mercuriusxeno.goo.client.radial;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/**
 * The glove's one radial wheel as pure layout and state. At rest every type
 * shows as a petal tip round the rim, clockwise from 12 in list order, around
 * a neutral zone reaching half the wheel, something chunky to mouse into.
 * The cursor out on a type's petal opens it: the hub shrinks back to its own
 * size and the types drop into the band round it, keeping their angles, and
 * the open type's abilities fan in the outer ring, centered on its petal,
 * each at most 30 degrees, tucked under the type petals' tips. Nothing ever
 * turns. Past either end of the fan the cursor winds on to the neighboring
 * type; back over the type band or the hub it returns the wheel to rest.
 * decision abilities-replace-the-hovered-type
 * decision ring-rotates-to-keep-the-cursor-inside
 * decision halves-arc-petals-at-rest
 *
 * <p>Angles run clockwise from the top, the way the petal masks are drawn.
 */
public final class RadialWheel {

    /** The wheel's diameter as a fraction of the smaller screen dimension. */
    static final double SCREEN_FRACTION = 0.98;
    /** The hub's radius with a type open, as a fraction of the wheel's. */
    static final double HUB_FRACTION = 0.2;
    /** The neutral zone's radius at rest, half the wheel's: the types stand outside it. */
    static final double HUB_REST_FRACTION = 0.5;
    /** Where the type petals start at rest, at the neutral zone's edge, as a fraction of the wheel's radius. */
    static final double TYPE_INNER_FRACTION = HUB_REST_FRACTION;
    /** How far out the type petals reach once dropped round the hub, as a fraction of the wheel's radius. */
    static final double TYPE_BAND_FRACTION = 0.6;
    /** How far under the dropped type petals' tips the ability petals start, as a fraction of the wheel's radius. */
    static final double ABILITY_TUCK = 0.05;
    /** Where the ability petals start, tucked under the dropped type petals' tips. */
    static final double ABILITY_INNER_FRACTION = TYPE_BAND_FRACTION - ABILITY_TUCK;
    /** No type open, or no ability hovered. */
    static final int NONE = -1;
    /** The most each ability petal of the open type takes. */
    static final double ABILITY_ARC = Math.toRadians(30.0);
    /** Where a ring starts: at 12, its petals running clockwise. */
    private static final double RING_START = 0.0;

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double HALF = 0.5;
    /** A scroll up steps the open type one petal counterclockwise. */
    private static final int STEP_BACK = -1;
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
     * The types the wheel shows, in list order: each offering at least one
     * ability, locked or not, and held above zero.
     * decision wheel-shows-only-held-types-with-abilities
     *
     * @param order    every type in list order
     * @param offered  the abilities a type offers, locked ones included
     * @param holdings the amount the player holds of a type
     * @param <T>      the type's key
     * @return the shown types
     */
    static <T> List<T> shownTypes(List<T> order, Function<T, List<?>> offered, ToIntFunction<T> holdings) {
        return order.stream()
                .filter(type -> !offered.apply(type).isEmpty() && holdings.applyAsInt(type) > 0)
                .toList();
    }

    /**
     * The wheel's outer radius for a screen: 98% of the smaller dimension, halved.
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
     * Each type's arc: the whole ring shared evenly, at rest and dropped alike.
     *
     * @return the arc in radians
     */
    double typeArc() {
        return TWO_PI / typeCount;
    }

    /**
     * The neutral zone's radius for how far the types have dropped: half the
     * wheel at rest, the hub's own with a type open.
     * decision halves-arc-petals-at-rest
     *
     * @param dropped how far the types have dropped, 0 to 1
     * @return the radius as a fraction of the wheel's
     */
    static double hubRadius(double dropped) {
        return HUB_REST_FRACTION - (HUB_REST_FRACTION - HUB_FRACTION) * dropped;
    }

    /**
     * The neutral zone's radius as drawn this frame.
     *
     * @param partialTick the fraction of a tick since the last one
     * @return the radius as a fraction of the wheel's
     */
    double displayedHubRadius(float partialTick) {
        return hubRadius(ease.displayed(partialTick).dropped());
    }

    /**
     * The target's petals in draw order: the open type's ability petals,
     * then every type petal over them. The cursor reads this layout, so the
     * wheel's state is settled the instant the target is and only the picture lags.
     * decision petal-moves-animate
     *
     * @return the petals
     */
    List<PetalArc> layout() {
        return petalsOf(targetPose(), (type, ability) -> liftTarget(type, ability) > 0 ? HOVER_REACH : 1.0);
    }

    /**
     * The petals as drawn this frame, easing toward {@link #layout()}.
     * decision petal-moves-animate
     *
     * @param partialTick the fraction of a tick since the last one
     * @return the petals on display, in draw order
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

    /** Advances the petals' ease one client tick. */
    public void tick() {
        ease.tick();
        for (int type = 0; type < typeCount; type++) {
            for (int ability = 0; ability < liftTicks[type].length; ability++) {
                liftTicks[type][ability] += Integer.signum(liftTarget(type, ability) - liftTicks[type][ability]);
            }
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
     * The target pose: at rest the types out at the tips and nothing fanned;
     * with a type open the types dropped and that type's abilities fanned.
     *
     * @return the target pose
     */
    private RingEase.Pose targetPose() {
        double[] openness = new double[typeCount];
        if (isOpen()) {
            openness[selectedType] = 1.0;
        }
        return new RingEase.Pose(isOpen() ? 1.0 : 0.0, openness);
    }

    /**
     * Lays a pose out: each fanning type's ability cards in the outer ring,
     * centered on its petal, fanned out from stacked on that center as far as
     * the type is open and tucked under the type petals' tips, then each
     * type's petal across its even share of the ring over them, from the
     * neutral zone's edge out to the rim at rest and in the band round the
     * hub once dropped.
     * decision petal-moves-animate
     * decision abilities-replace-the-hovered-type
     *
     * @param pose       the pose
     * @param cardLength each ability petal's length, by type and ability index
     * @return the ability cards, then the type petals drawn over them
     */
    private List<PetalArc> petalsOf(RingEase.Pose pose, CardLength cardLength) {
        List<PetalArc> petals = new ArrayList<>();
        for (int type = 0; type < typeCount; type++) {
            addCards(petals, type, pose.openness()[type], cardLength);
        }
        double arc = typeArc();
        double inner = hubRadius(pose.dropped());
        double length = 1.0 - (1.0 - TYPE_BAND_FRACTION) * pose.dropped();
        for (int type = 0; type < typeCount; type++) {
            double start = RING_START + type * arc;
            petals.add(new PetalArc(type, NONE, start, arc, length, null, pose.openness()[type], 1.0, inner));
        }
        return petals;
    }

    private void addCards(List<PetalArc> petals, int type, double openness, CardLength cardLength) {
        int abilities = abilityCount.applyAsInt(type);
        if (abilities == 0 || openness <= VISIBLE) {
            return;
        }
        double card = abilityArc(type);
        double center = RING_START + (type + HALF) * typeArc();
        double span = abilities * card;
        for (int ability = 0; ability < abilities; ability++) {
            double fanned = (ability * card + card * HALF - span * HALF) * openness;
            petals.add(new PetalArc(type, ability, center - card * HALF + fanned, card,
                    cardLength.of(type, ability), null, openness, 1.0, ABILITY_INNER_FRACTION));
        }
    }

    /**
     * Each of a type's ability petals' arc: 30 degrees, or the whole ring shared evenly when that many do not fit.
     * decision fisheye-halves-its-max-and-floors-at-ten-degrees
     *
     * @param type the type
     * @return the arc in radians
     */
    private double abilityArc(int type) {
        return Math.min(ABILITY_ARC, TWO_PI / abilityCount.applyAsInt(type));
    }

    /**
     * The petal of a layout an angle falls on.
     *
     * @param petals the layout, its first petal starting anywhere
     * @param angle  the angle, any turn
     * @return the petal holding the angle, else the nearest one clockwise of it
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
     * Moves the cursor. At rest the neutral zone holds nothing and the cursor
     * out on a type's petal opens that type. With a type open the cursor back
     * over the type band or the hub returns the wheel to rest, and out past
     * the band it hovers the ability under it, or past either end of the fan
     * winds on to the neighboring type.
     * decision abilities-replace-the-hovered-type
     * decision halves-arc-petals-at-rest
     *
     * @param dx          the cursor's x offset from the center
     * @param dy          the cursor's y offset from the center, down positive
     * @param outerRadius the wheel's outer radius
     */
    public void moveCursor(double dx, double dy, double outerRadius) {
        if (typeCount == 0) {
            return;
        }
        double distance = Math.hypot(dx, dy) / outerRadius;
        double angle = angleOf(dx, dy);
        if (!isOpen()) {
            if (distance >= TYPE_INNER_FRACTION) {
                openUnder(typeAt(angle), angle);
            }
        } else if (distance < TYPE_BAND_FRACTION) {
            returnToRest();
        } else {
            hoverAt(angle);
        }
    }

    /**
     * The cursor out past the type band with a type open: it hovers the
     * ability under it, and past either end of the fan winds on to the
     * neighboring type that way, clockwise to the next and counterclockwise
     * to the last; a type with no abilities has no fan to wind past.
     * decision ring-rotates-to-keep-the-cursor-inside
     *
     * @param angle the cursor's angle
     */
    private void hoverAt(double angle) {
        PetalArc under = abilityAt(angle);
        if (under != null) {
            hoveredAbility = under.ability();
            return;
        }
        hoveredAbility = NONE;
        List<PetalArc> fan = layout().stream().filter(PetalArc::isAbility).toList();
        if (fan.isEmpty()) {
            return;
        }
        double fanCenter = RING_START + (selectedType + HALF) * typeArc();
        int step = Math.IEEEremainder(angle - fanCenter, TWO_PI) > 0 ? 1 : STEP_BACK;
        openUnder(Math.floorMod(selectedType + step, typeCount), angle);
    }

    /**
     * Opens a type, its abilities centered on its petal, hovering whichever is under the cursor.
     * decision abilities-replace-the-hovered-type
     *
     * @param type  the type to open
     * @param angle the cursor's angle
     */
    private void openUnder(int type, double angle) {
        openType(type);
        PetalArc under = abilityAt(angle);
        hoveredAbility = under == null ? NONE : under.ability();
    }

    /**
     * The type whose petal an angle falls on.
     *
     * @param angle the angle
     * @return the type index
     */
    private int typeAt(double angle) {
        return Math.min(typeCount - 1, (int) (wrap(angle - RING_START) / typeArc()));
    }

    /**
     * The open type's ability petal an angle falls on.
     *
     * @param angle the angle
     * @return the petal, or null past the fan
     */
    private @Nullable PetalArc abilityAt(double angle) {
        return layout().stream().filter(PetalArc::isAbility)
                .filter(petal -> wrap(angle - petal.start()) < petal.arc())
                .findFirst().orElse(null);
    }

    /** Closes the open type, the types going back out to the tips and the neutral zone back out. */
    private void returnToRest() {
        boolean wasOpen = isOpen();
        selectedType = NONE;
        hoveredAbility = NONE;
        if (wasOpen) {
            ease.retarget(targetPose());
        }
    }

    /**
     * Opens a type: the types drop into the band and its abilities fan out centered on its petal.
     * decision abilities-replace-the-hovered-type
     *
     * @param type the type to open
     */
    private void openType(int type) {
        selectedType = type;
        hoveredAbility = NONE;
        ease.retarget(targetPose());
    }

    /**
     * Steps the hovered ability with the scroll wheel, through every ability
     * of every type in list order: past either end of the open type's
     * abilities it opens the next or previous type with abilities, wrapping
     * around the list, so the radial works on a controller.
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
                openHovering(type, step > 0 ? 0 : abilities - 1);
                return;
            }
        }
    }

    /**
     * Opens a type, unless already open, and hovers one of its abilities.
     *
     * @param type    the type
     * @param ability the ability to hover
     */
    private void openHovering(int type, int ability) {
        if (type != selectedType) {
            openType(type);
        }
        hoveredAbility = ability;
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
     * Whether a type is open, its abilities fanned in the outer ring.
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
     * One petal: a type's own petal, or one ability petal of the open type.
     *
     * @param type     the type index the petal belongs to
     * @param ability  the ability index, or {@link #NONE} for the type's own petal
     * @param start    the petal's start angle, clockwise from the top
     * @param arc      the petal's span in radians
     * @param length   the petal's outer radius as a fraction of the wheel's
     * @param root     the petal this one starts out from, or null to start at its inner radius
     * @param openness how far the petal's type is open, 0 closed to 1 fanned, which the
     *                 ability petal's content slides in by
     * @param presence how much of the petal shows, 0 hidden to 1 full, which its fill and icon fade by
     * @param inner    where the petal starts, as a fraction of the wheel's radius
     */
    record PetalArc(int type, int ability, double start, double arc, double length,
                    PetalMask.@Nullable Petal root, double openness, double presence, double inner) {

        /**
         * A petal starting where the type petals start at rest.
         *
         * @param type     the type index
         * @param ability  the ability index, or {@link #NONE}
         * @param start    the start angle
         * @param arc      the span
         * @param length   the outer radius
         * @param root     the petal it starts out from, or null
         * @param openness how far the type is open
         * @param presence how much of the petal shows
         */
        PetalArc(int type, int ability, double start, double arc, double length, PetalMask.@Nullable Petal root,
                 double openness, double presence) {
            this(type, ability, start, arc, length, root, openness, presence, TYPE_INNER_FRACTION);
        }

        /**
         * The petal's shape: from its inner radius, or from exactly where its root ends.
         * decision petal-moves-animate
         *
         * @return the shape
         */
        PetalMask.Petal shape() {
            return new PetalMask.Petal(start, arc, root == null ? inner : root.outer(), length, root);
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
