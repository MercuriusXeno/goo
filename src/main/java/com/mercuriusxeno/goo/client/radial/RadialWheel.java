package com.mercuriusxeno.goo.client.radial;

import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/**
 * The glove's one radial wheel as pure layout and state. At rest two side
 * pickers stand between the hub and the type ring, A..M on the right and
 * N..Z on the left, each type belonging to the side its name's first letter
 * falls in, and every type shows clockwise from 12: A..M down the right,
 * N..Z back up the left. The cursor on a side's picker or any of its type
 * petals opens that side: the pickers go, and the side's types fill the
 * whole ring clockwise from 12, reaching in to the hub. Hovering a type (or
 * scrolling to it) opens it with no click: its petal is replaced in the ring
 * by its ability petals, its neighbors squeezing and fading with distance.
 * The ring rests with the open type's abilities centered where its petal
 * sat, and opening one turns the ring only to land the cursor on the ability
 * that brings the type's center nearest rest. The cursor in the hub returns
 * the wheel to rest.
 * decision abilities-replace-the-hovered-type
 * decision open-type-keeps-thirteen-fading-to-twenty-percent
 * decision fisheye-halves-its-max-and-floors-at-ten-degrees
 * decision ring-rotates-to-keep-the-cursor-inside
 * decision halves-arc-petals-at-rest
 * decision open-half-spans-270-degrees
 *
 * <p>Angles run clockwise from the top, the way the petal masks are drawn.
 */
public final class RadialWheel {

    /** The A..M side, on the right. */
    static final int RIGHT = 0;
    /** The N..Z side, on the left. */
    static final int LEFT = 1;
    /** The two ways round a ring, which halve the farthest any type stands from another. */
    private static final int WAYS_ROUND = 2;
    /** Where a ring starts: at 12, its types running clockwise. */
    private static final double RING_START = 0.0;
    /** How many sides the types split into. */
    private static final int HALVES = 2;
    /** The last letter a type's name starts with to belong to the right side. */
    private static final char LAST_RIGHT_LETTER = 'm';
    /** Each side picker's arc: half a turn, A..M from 12 round to 6, N..Z from 6 round to 12. */
    static final double SIDE_ARC = Math.PI;
    /** Where the type petals start at rest, as a fraction of the wheel's radius, the side pickers inside them. */
    static final double TYPE_INNER_FRACTION = 0.4;
    /** The gap between a side picker's outer edge and the type ring, as a fraction of the wheel's radius. */
    static final double BAND_GAP = 0.02;
    /** The wheel's diameter as a fraction of the smaller screen dimension. */
    static final double SCREEN_FRACTION = 0.98;
    /** The hub's radius as a fraction of the wheel's: the cursor inside it returns the wheel to rest. */
    static final double HUB_FRACTION = 0.2;
    /** No type open, or no ability hovered. */
    static final int NONE = -1;
    /** The most each ability petal of the open type takes once the abilities outgrow the type's rest slot. */
    static final double ABILITY_ARC = Math.toRadians(30.0);
    /** The narrowest arc a neighbor squeezes to: the farthest's at full squeeze. */
    static final double NEIGHBOR_MIN_ARC = Math.toRadians(10.0);
    /** Halvings the squeeze's search takes, well past a double's precision. */
    private static final int SQUEEZE_STEPS = 60;
    /** How many types either side of the open type stay on the ring. */
    static final int NEIGHBORS_A_SIDE = 6;
    /** The most types a wheel holds with none hidden while one is open: the open type and its neighbors. */
    private static final int WINDOW_TYPES = NEIGHBORS_A_SIDE + 1 + NEIGHBORS_A_SIDE;
    /** The farthest neighbor's presence, the floor the fade steps down to. */
    static final double NEIGHBOR_MIN_PRESENCE = 0.2;

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double HALF = 0.5;
    /** A scroll up steps the open type one petal counterclockwise. */
    private static final int STEP_BACK = -1;
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
    /** Each type's half, by type index. */
    private final int[] halfOfType;
    /** Each type's place among its half's types, in list order. */
    private final int[] placeInHalf;
    /** Each half's type indices, in list order. */
    private final int[][] members;
    /** The open half, or {@link #NONE} at rest. */
    private int openHalf = NONE;
    private int selectedType = NONE;
    private int hoveredAbility = NONE;
    /** Per type and ability, the ticks its petal has grown toward the screen's edge. */
    private final int[][] liftTicks;
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
     * @param halfOf       the half a type index belongs to, {@link #RIGHT} or {@link #LEFT}
     */
    public RadialWheel(int typeCount, IntUnaryOperator abilityCount, IntUnaryOperator halfOf) {
        this.typeCount = typeCount;
        this.abilityCount = abilityCount;
        this.halfOfType = new int[typeCount];
        this.placeInHalf = new int[typeCount];
        int[] sizes = new int[HALVES];
        for (int type = 0; type < typeCount; type++) {
            halfOfType[type] = halfOf.applyAsInt(type);
            placeInHalf[type] = sizes[halfOfType[type]]++;
        }
        this.members = new int[][]{new int[sizes[RIGHT]], new int[sizes[LEFT]]};
        for (int type = 0; type < typeCount; type++) {
            members[halfOfType[type]][placeInHalf[type]] = type;
        }
        this.ease = new RingEase(targetPose());
        this.liftTicks = new int[typeCount][];
        for (int type = 0; type < typeCount; type++) {
            liftTicks[type] = new int[abilityCount.applyAsInt(type)];
        }
    }

    /**
     * The side a type's name puts it in: A..M the right, the rest the left.
     * decision halves-arc-petals-at-rest
     *
     * @param name the type's name
     * @return {@link #RIGHT} or {@link #LEFT}
     */
    public static int halfOfName(String name) {
        return !name.isEmpty() && Character.toLowerCase(name.charAt(0)) <= LAST_RIGHT_LETTER ? RIGHT : LEFT;
    }

    /**
     * The side picker an angle falls on: the right from 12 round to 6, the left from 6 round to 12.
     * decision halves-arc-petals-at-rest
     *
     * @param angle the angle
     * @return {@link #RIGHT} or {@link #LEFT}
     */
    private static int sideAt(double angle) {
        return wrap(angle) < SIDE_ARC ? RIGHT : LEFT;
    }

    /**
     * How much of the side pickers the target shows.
     *
     * @return 1 at rest, 0 with a side open
     */
    private double pickersTarget() {
        return openHalf == NONE ? 1.0 : 0.0;
    }

    /**
     * Where the N..Z side's ring starts: 6 o'clock at rest, after A..M, and 12 with that side open.
     *
     * @return the angle in radians
     */
    private double leftStartTarget() {
        return openHalf == LEFT ? RING_START : RING_START + SIDE_ARC;
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
     * The target's two side pickers, A..M from 12 round to 6 and N..Z from 6 round to 12, shown at rest only.
     * decision halves-arc-petals-at-rest
     *
     * @return the right picker, then the left, empty with a side open
     */
    List<HalfPetal> halves() {
        return halvesOf(pickersTarget());
    }

    /**
     * The side pickers as drawn this frame, shrinking into the hub as a side opens.
     * decision petal-moves-animate
     *
     * @param partialTick the fraction of a tick since the last one
     * @return the right picker, then the left, empty once they have gone
     */
    List<HalfPetal> displayedHalves(float partialTick) {
        return halvesOf(ease.displayed(partialTick).pickers());
    }

    private static List<HalfPetal> halvesOf(double pickers) {
        if (pickers <= VISIBLE) {
            return List.of();
        }
        double outer = HUB_FRACTION + (TYPE_INNER_FRACTION - BAND_GAP - HUB_FRACTION) * pickers;
        return List.of(new HalfPetal(RIGHT, RING_START, SIDE_ARC, outer),
                new HalfPetal(LEFT, RING_START + SIDE_ARC, SIDE_ARC, outer));
    }

    /**
     * Where the type petals start for an amount of picker showing: out past the pickers at rest, at the hub once they go.
     *
     * @param pickers how much of the side pickers shows
     * @return the inner radius as a fraction of the wheel's
     */
    private static double typeInner(double pickers) {
        return HUB_FRACTION + (TYPE_INNER_FRACTION - HUB_FRACTION) * pickers;
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
     * The target pose. At rest every type shows on its side. With a side open and no
     * type fanned out, the half's types share the whole ring evenly, closed
     * and full. With a type fanned out, its slot is as wide as its abilities
     * and fully open, and each other type of its half takes its squeezed arc
     * at its presence, the ring filled; the other half's types take no width
     * and no presence.
     * decision open-type-keeps-thirteen-fading-to-twenty-percent
     * decision halves-arc-petals-at-rest
     * decision open-half-spans-270-degrees
     *
     * @return the target pose
     */
    private RingEase.Pose targetPose() {
        if (!isFannedOut()) {
            return closedPose();
        }
        double[] widths = new double[typeCount];
        double[] openness = new double[typeCount];
        double[] presence = new double[typeCount];
        int fanned = selectedType;
        Arcs arcs = arcsWithOpenType(fanned);
        for (int type = 0; type < typeCount; type++) {
            presence[type] = type == fanned ? 1.0 : presenceAround(fanned, type);
            widths[type] = type == fanned ? abilityCount.applyAsInt(type) * arcs.ability()
                    : neighborArc(fanned, type, arcs.squeeze());
        }
        openness[fanned] = 1.0;
        return new RingEase.Pose(centering(widths) + rotation, widths, openness, presence, pickersTarget(),
                leftStartTarget());
    }

    /**
     * Whether an open type holding abilities fans out on an open half.
     *
     * @return true when the ring holds ability petals
     */
    private boolean isFannedOut() {
        return openHalf != NONE && isOpen() && abilityCount.applyAsInt(selectedType) > 0;
    }

    /**
     * The turn that centers the open type's abilities on where its own petal
     * sat in its half's even ring, so at rest a type fans out in place and
     * the ring's tension is how far it has turned from there.
     * decision ring-rotates-to-keep-the-cursor-inside
     *
     * @param widths each type's slot width
     * @return the turn in radians, within half a turn either way
     */
    private double centering(double[] widths) {
        double before = 0.0;
        for (int type = 0; type < selectedType; type++) {
            before += widths[type];
        }
        double abilitiesCenter = RING_START + before + widths[selectedType] * HALF;
        double satAt = RING_START + (placeInHalf[selectedType] + HALF) * restArc(selectedType);
        return Math.IEEEremainder(satAt - abilitiesCenter, TWO_PI);
    }

    /**
     * The pose with no type fanned out: at rest every type closed and full,
     * each side's types sharing its half turn evenly; with a side open, its
     * types sharing the whole ring evenly and the other side's gone.
     * decision halves-arc-petals-at-rest
     * decision open-half-spans-270-degrees
     *
     * @return the pose at the current rotation
     */
    private RingEase.Pose closedPose() {
        double[] widths = new double[typeCount];
        double[] presence = new double[typeCount];
        for (int type = 0; type < typeCount; type++) {
            int side = halfOfType[type];
            if (openHalf == NONE || openHalf == side) {
                widths[type] = (openHalf == NONE ? SIDE_ARC : TWO_PI) / members[side].length;
                presence[type] = 1.0;
            }
        }
        return new RingEase.Pose(rotation, widths, new double[typeCount], presence, pickersTarget(),
                leftStartTarget());
    }

    /**
     * A type's arc while its half is open and no type fanned out: the whole
     * ring shared evenly, the most any type petal of that half ever takes.
     *
     * @param type the type
     * @return the arc in radians
     */
    private double restArc(int type) {
        return TWO_PI / members[halfOfType[type]].length;
    }

    /**
     * How far round its half's ring, either way, a type stands from the open type.
     *
     * @param open the open type
     * @param type another type of the same half
     * @return the fewer steps of the two ways round
     */
    private int distance(int open, int type) {
        int ring = members[halfOfType[open]].length;
        int from = placeInHalf[open];
        int to = placeInHalf[type];
        return Math.min(Math.floorMod(to - from, ring), Math.floorMod(from - to, ring));
    }

    /**
     * A type's presence while another is open: none outside the open type's
     * half; on a half the neighbors hold whole every type stays at full
     * presence; otherwise the next and last stand full, each step farther
     * fades by an even step to 20% at the sixth, and the rest stay at 20%.
     * decision open-type-keeps-thirteen-fading-to-twenty-percent
     *
     * @param open the open type
     * @param type the other type
     * @return 0 hidden to 1 full
     */
    private double presenceAround(int open, int type) {
        if (halfOfType[type] != halfOfType[open]) {
            return 0.0;
        }
        if (members[halfOfType[open]].length <= WINDOW_TYPES) {
            return 1.0;
        }
        int steps = Math.min(distance(open, type), NEIGHBORS_A_SIDE);
        return 1.0 - (1.0 - NEIGHBOR_MIN_PRESENCE) * stepsOut(steps);
    }

    /**
     * How far through the window a distance stands.
     *
     * @param steps the distance from the open type, 1 to the window's edge
     * @return 0 at the next and last neighbor to 1 at the sixth
     */
    private static double stepsOut(int steps) {
        return (steps - 1.0) / (NEIGHBORS_A_SIDE - 1);
    }

    /**
     * Whether a type stands on the ring beside the open type.
     *
     * @param open the open type
     * @param type the other type
     * @return true for a neighbor the window keeps
     */
    private boolean isVisibleNeighbor(int open, int type) {
        return type != open && presenceAround(open, type) > 0;
    }

    /**
     * How far round its half's ring the farthest type stands from the open type.
     *
     * @param open the open type
     * @return the steps, the ring walked either way round
     */
    private int farthestDistance(int open) {
        return members[halfOfType[open]].length / WAYS_ROUND;
    }

    /**
     * A neighbor's arc: its rest arc, shrunk by the squeeze in proportion to
     * its distance, so the next and last shrink least and the farthest
     * most, never past the floor and never wider than at rest.
     * decision fisheye-halves-its-max-and-floors-at-ten-degrees
     *
     * @param open    the open type
     * @param type    the other type
     * @param squeeze how far the ring squeezes its neighbors, 1 bringing the farthest to the floor
     * @return the arc in radians, none outside the open type's half
     */
    private double neighborArc(int open, int type, double squeeze) {
        if (!isVisibleNeighbor(open, type)) {
            return 0.0;
        }
        double rest = restArc(open);
        double floor = Math.min(NEIGHBOR_MIN_ARC, rest);
        double reach = distance(open, type) / (double) farthestDistance(open);
        return Math.max(floor, rest - squeeze * reach * (rest - floor));
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
        // halves-arc-petals-at-rest: A..M runs clockwise from 12, N..Z from where A..M ends, or from 12 when open
        double[] starts = {RING_START + pose.rotation(), pose.leftStart() + pose.rotation()};
        double inner = typeInner(pose.pickers());
        for (int type = 0; type < typeCount; type++) {
            double openness = pose.openness()[type];
            double width = pose.widths()[type];
            double length = 1.0 - (1.0 - TYPE_BASE_LENGTH) * openness;
            double presence = pose.presence()[type];
            double start = starts[halfOfType[type]];
            PetalMask.Petal base = new PetalMask.Petal(start, width, inner, length);
            addCards(petals, type, new Slot(start, openness, base), cardLength);
            if (width > 0) {
                petals.add(new PetalArc(type, NONE, start, width, length, null, openness, presence, inner));
            }
            starts[halfOfType[type]] += width;
        }
        return petals;
    }

    private void addCards(List<PetalArc> petals, int type, Slot slot, CardLength cardLength) {
        int abilities = abilityCount.applyAsInt(type);
        if (abilities == 0 || slot.openness() <= VISIBLE) {
            return;
        }
        double card = arcsWithOpenType(type).ability();
        for (int ability = abilities - 1; ability >= 0; ability--) {
            petals.add(new PetalArc(type, ability, slot.start() + ability * card * slot.openness(), card,
                    cardLength.of(type, ability), slot.base(), slot.openness(), 1.0));
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
     * Sizes the ring around an open type so it fills the whole turn with no
     * petal wider than at rest. Abilities that fit in the type's own rest
     * slot share it and the neighbors keep their rest arcs. Otherwise each
     * ability takes its 30 degrees and the neighbors squeeze, the farthest
     * most, until the ring is full; with every neighbor at the floor and
     * still no room, the abilities share what remains, under 30 degrees.
     * decision fisheye-halves-its-max-and-floors-at-ten-degrees
     *
     * @param open the open type, holding at least one ability
     * @return the arc of each ability and the neighbors' squeeze
     */
    private Arcs arcsWithOpenType(int open) {
        int abilities = abilityCount.applyAsInt(open);
        double rest = restArc(open);
        if (abilities * ABILITY_ARC <= rest) {
            return new Arcs(rest / abilities, 0.0);
        }
        double room = TWO_PI - abilities * ABILITY_ARC;
        double fullSqueeze = farthestDistance(open);
        double squeezed = neighborsAt(open, fullSqueeze);
        if (squeezed >= room) {
            return new Arcs((TWO_PI - squeezed) / abilities, fullSqueeze);
        }
        double low = 0.0;
        double high = fullSqueeze;
        for (int step = 0; step < SQUEEZE_STEPS; step++) {
            double middle = (low + high) * HALF;
            if (neighborsAt(open, middle) > room) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return new Arcs(ABILITY_ARC, high);
    }

    /**
     * The open type's neighbors' arcs summed at a squeeze.
     *
     * @param open    the open type
     * @param squeeze the squeeze
     * @return the sum in radians
     */
    private double neighborsAt(int open, double squeeze) {
        double sum = 0.0;
        for (int type = 0; type < typeCount; type++) {
            sum += neighborArc(open, type, squeeze);
        }
        return sum;
    }

    /**
     * One open type's ring sizes.
     *
     * @param ability the arc of each ability petal
     * @param squeeze how far the neighbors shrink from their rest arcs
     */
    private record Arcs(double ability, double squeeze) {
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
     * Moves the cursor: the hub returns the wheel to rest; at rest the
     * cursor on a side's picker or any of its type petals opens that side.
     * With a side open, the cursor anywhere outside the hub reads the ring:
     * an ability petal of the open type hovers it, and another type's petal
     * opens that type.
     * decision abilities-replace-the-hovered-type
     * decision ring-rotates-to-keep-the-cursor-inside
     * decision halves-arc-petals-at-rest
     * decision open-half-spans-270-degrees
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
        if (openHalf == NONE) {
            openHalf(sideAt(cursorAngle));
        } else {
            moveOnTheRing();
        }
    }

    /**
     * The cursor on the open side's ring hovers or opens the petal under it.
     * decision ring-rotates-to-keep-the-cursor-inside
     */
    private void moveOnTheRing() {
        layout().stream().filter(petal -> wrap(cursorAngle - petal.start()) < petal.arc())
                .findFirst().ifPresent(this::landOn);
    }

    /**
     * Opens a side with no type open, its ring unturned, the pickers going.
     * decision open-half-spans-270-degrees
     *
     * @param half the half to open
     */
    private void openHalf(int half) {
        openHalf = half;
        selectedType = NONE;
        hoveredAbility = NONE;
        rotation = 0.0;
        ease.retarget(targetPose());
    }

    /**
     * The cursor on a petal: another type's petal opens that type, and an
     * ability of the open type hovers it with the ring holding still.
     * decision ring-rotates-to-keep-the-cursor-inside
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

    /**
     * Of the open type's abilities, the one whose middle the least turn from
     * the unturned rest brings under the cursor, and that turn.
     * decision ring-rotates-to-keep-the-cursor-inside
     *
     * @return the ability and the ring's rotation that centers it on the cursor
     */
    private Landing landingNearestRest() {
        double turned = rotation;
        rotation = 0.0;
        List<PetalArc> unturned = layout();
        rotation = turned;
        Landing best = new Landing(NONE, Double.POSITIVE_INFINITY);
        for (PetalArc petal : unturned) {
            double turn = Math.IEEEremainder(cursorAngle - petal.center(), TWO_PI);
            if (petal.isAbility() && Math.abs(turn) < Math.abs(best.turn())) {
                best = new Landing(petal.ability(), turn);
            }
        }
        return best;
    }

    /**
     * Where the cursor lands among the open type's abilities.
     *
     * @param ability the ability under the cursor
     * @param turn    the ring's rotation that centers it there
     */
    private record Landing(int ability, double turn) {
    }

    /**
     * Closes the open half and type and turns the ring back, the cursor in the hub.
     * decision halves-arc-petals-at-rest
     */
    private void returnToRest() {
        boolean wasOpen = openHalf != NONE;
        openHalf = NONE;
        selectedType = NONE;
        hoveredAbility = NONE;
        rotation = 0.0;
        cursorAngle = Double.NaN;
        if (wasOpen) {
            ease.retarget(targetPose());
        }
    }

    /**
     * Opens a type, the cursor landing in whichever of its abilities the
     * least turn from the unturned rest brings under it, that ability's
     * middle on the cursor. With no cursor angle, or no ability, the ring
     * rests unturned.
     * decision ring-rotates-to-keep-the-cursor-inside
     *
     * @param type the type to open
     */
    private void openType(int type) {
        selectedType = type;
        hoveredAbility = NONE;
        rotation = 0.0;
        if (!Double.isNaN(cursorAngle) && abilityCount.applyAsInt(type) > 0) {
            Landing landing = landingNearestRest();
            rotation = landing.turn();
            hoveredAbility = landing.ability();
        }
        ease.retarget(targetPose());
    }

    /**
     * Steps the hovered ability with the scroll wheel, through every ability
     * of every type in list order: past either end of the open type's
     * abilities it opens the next or previous type with abilities, wrapping
     * around the list, so the radial works on a controller, each type's half
     * opening as the step reaches it. A controller has no cursor for the
     * ring to follow, so the ring rests, each type fanning out in place.
     * decision abilities-replace-the-hovered-type
     * decision open-half-spans-270-degrees
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
                openCentered(type, step > 0 ? 0 : abilities - 1);
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
     * Opens a type's half and the type with one of its abilities hovered,
     * the ring at rest, so the type's abilities fan out where its petal sat.
     * decision open-half-spans-270-degrees
     *
     * @param type    the type to open
     * @param ability the ability to hover
     */
    private void openCentered(int type, int ability) {
        boolean changed = type != selectedType || rotation != 0.0;
        openHalf = halfOfType[type];
        selectedType = type;
        hoveredAbility = ability;
        rotation = 0.0;
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
     *                which starts at its inner radius
     * @param openness how far the petal's type is open, 0 closed to 1 fanned, which the
     *                 ability petal's content slides in by
     * @param presence how much of the petal shows, 0 hidden to 1 full, which its fill and icon fade by
     * @param inner    where a type petal starts, as a fraction of the wheel's radius: past the side pickers
     *                 at rest, at the hub with a side open
     */
    record PetalArc(int type, int ability, double start, double arc, double length,
                    PetalMask.@Nullable Petal root, double openness, double presence, double inner) {

        /**
         * A petal starting past the side pickers, where the type ring stands at rest.
         *
         * @param type     the type index
         * @param ability  the ability index, or {@link #NONE}
         * @param start    the start angle
         * @param arc      the span
         * @param length   the outer radius
         * @param root     the type petal an ability petal starts out from, or null
         * @param openness how far the type is open
         * @param presence how much of the petal shows
         */
        PetalArc(int type, int ability, double start, double arc, double length, PetalMask.@Nullable Petal root,
                 double openness, double presence) {
            this(type, ability, start, arc, length, root, openness, presence, TYPE_INNER_FRACTION);
        }

        /**
         * The petal's shape: from its inner radius for a type petal, from exactly where
         * its type petal ends for an ability petal.
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
     * One side picker, in the band between the hub and the type ring.
     * decision halves-arc-petals-at-rest
     *
     * @param half  {@link #RIGHT} for A..M, {@link #LEFT} for N..Z
     * @param start the picker's start angle, clockwise from the top
     * @param arc   the picker's span in radians
     * @param outer how far out it reaches, as a fraction of the wheel's radius, shrinking into the hub as a side opens
     */
    record HalfPetal(int half, double start, double arc, double outer) {

        /**
         * The picker's shape: from the hub out to its reach.
         *
         * @return the shape
         */
        PetalMask.Petal shape() {
            return new PetalMask.Petal(start, arc, HUB_FRACTION, outer);
        }

        /**
         * The angle at the petal's middle, which its letter range centers on.
         *
         * @return the angle in radians
         */
        double center() {
            return start + arc * HALF;
        }
    }

    /**
     * The open half.
     *
     * @return {@link #RIGHT}, {@link #LEFT}, or {@link #NONE} at rest
     */
    public int openHalf() {
        return openHalf;
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
