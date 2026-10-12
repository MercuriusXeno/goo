package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the radial wheel's layout and its state transitions from cursor, scroll and click events; every type
 * sits in the A..M half unless a case names both halves (decisions abilities-replace-the-hovered-type,
 * open-type-keeps-thirteen-fading-to-twenty-percent, fisheye-halves-its-max-and-floors-at-ten-degrees,
 * halves-arc-petals-at-rest, open-half-spans-270-degrees).
 */
class RadialWheelTest {

    /** The shipped goo types. */
    private static final int TYPES = 26;
    /** The middle type, whose ring stays clear of the held half's 90 degrees. */
    private static final int OPEN_TYPE = 13;
    private static final int ABILITIES = 4;
    /** Six 30-degree petals leave 180 degrees, short of the neighbors' full 225, so the steps flatten. */
    private static final int SIX_ABILITIES = 6;
    /** Twelve 30-degree petals leave less than the twelve neighbors' 120 degree floor. */
    private static final int TWELVE_ABILITIES = 12;
    private static final int NEIGHBORS = 2 * RadialWheel.NEIGHBORS_A_SIDE;
    /** The fewest types a wheel hides none of while one is open: the open type and six either side. */
    private static final int WINDOW_TYPES = NEIGHBORS + 1;
    private static final double RADIUS = 100.0;
    private static final double PETAL = (RadialWheel.TYPE_INNER_FRACTION + 1.0) / 2 * RADIUS;
    /** Halfway across the band the half petals stand in. */
    private static final double BAND = (RadialWheel.HUB_FRACTION + RadialWheel.TYPE_INNER_FRACTION) / 2 * RADIUS;
    private static final double HUB = RadialWheel.HUB_FRACTION / 2 * RADIUS;
    private static final double TWO_PI = 2.0 * Math.PI;
    /** Each type's share of the open half's window while no type is open. */
    private static final double HALF_TYPE_ARC = RadialWheel.HALF_OPEN_ARC / TYPES;
    private static final double EPSILON = 1e-9;
    private static final double TOP = 0.0;
    private static final double BOTTOM = Math.PI;

    /** A wheel of the shipped type count, every type in the A..M half, each opening to the same abilities. */
    private static RadialWheel wheel(int abilities) {
        return new RadialWheel(TYPES, type -> abilities, type -> RadialWheel.TOP);
    }

    /** The first type of the N..Z half in the two-half wheel. */
    private static final int FIRST_BOTTOM = TYPES / 2;

    /** Two abilities a type, the first half of the list A..M and the rest N..Z. */
    private static RadialWheel wheelOverBothHalves() {
        return new RadialWheel(TYPES, type -> 2, type -> type < FIRST_BOTTOM ? RadialWheel.TOP : RadialWheel.BOTTOM);
    }

    /** Moves the cursor to a clockwise angle from the top at a distance from the center. */
    private static void moveTo(RadialWheel wheel, double angle, double distance) {
        wheel.moveCursor(Math.sin(angle) * distance, -Math.cos(angle) * distance, RADIUS);
    }

    /** Moves the cursor onto the top half petal, then onto a type's petal in the open half. */
    private static void hoverType(RadialWheel wheel, int type) {
        if (wheel.openHalf() == RadialWheel.NONE) {
            moveTo(wheel, TOP, BAND);
        }
        moveTo(wheel, typePetal(wheel.layout(), type).center(), PETAL);
    }

    /** Opens a type under the cursor and lets the fan and the grace window settle. */
    private static RadialWheel opened(int abilities) {
        RadialWheel wheel = wheel(abilities);
        hoverType(wheel, OPEN_TYPE);
        settle(wheel);
        return wheel;
    }

    private static void settle(RadialWheel wheel) {
        tick(wheel, Math.max(RingEase.DURATION_TICKS, RadialWheel.GRACE_TICKS));
    }

    private static void tick(RadialWheel wheel, int ticks) {
        for (int i = 0; i < ticks; i++) {
            wheel.tick();
        }
    }

    private static RadialWheel.PetalArc abilityPetal(RadialWheel wheel, int ability) {
        return wheel.layout().stream().filter(petal -> petal.ability() == ability).findFirst().orElseThrow();
    }

    private static List<RadialWheel.PetalArc> typePetals(RadialWheel wheel) {
        return wheel.layout().stream().filter(petal -> !petal.isAbility()).toList();
    }

    private static List<RadialWheel.PetalArc> abilityPetals(RadialWheel wheel) {
        return wheel.layout().stream().filter(RadialWheel.PetalArc::isAbility).toList();
    }

    private static RadialWheel.PetalArc typePetal(List<RadialWheel.PetalArc> petals, int type) {
        return petals.stream().filter(petal -> !petal.isAbility() && petal.type() == type).findFirst().orElseThrow();
    }

    /** The type a number of steps clockwise of another, wrapping round the list. */
    private static int stepsFrom(int type, int steps) {
        return Math.floorMod(type + steps, TYPES);
    }

    @Nested
    class Layout {

        @Test
        void radiusIsNinetyEightPercentOfTheSmallerDimensionHalved() {
            assertEquals(529.2, RadialWheel.outerRadius(1920, 1080), EPSILON);
            assertEquals(392, RadialWheel.outerRadius(800, 1200), EPSILON);
        }

        /** decision halves-arc-petals-at-rest */
        @Test
        void restShowsNoTypePetalOnlyTwoHalfPetalsInTheBandOutsideTheHub() {
            RadialWheel wheel = wheel(ABILITIES);
            List<RadialWheel.HalfPetal> halves = wheel.halves();

            assertTrue(wheel.layout().isEmpty(), "a type petal shows at rest");
            assertEquals(2, halves.size());
            assertAll(halves.stream().map(half -> (Executable) () -> {
                assertEquals(Math.PI, half.arc(), EPSILON, "half " + half.half());
                assertEquals(RadialWheel.HUB_FRACTION, half.shape().inner(), EPSILON);
                assertTrue(half.shape().outer() < RadialWheel.TYPE_INNER_FRACTION, "outer " + half.shape().outer());
            }));
            assertEquals(TOP, Math.IEEEremainder(halves.get(RadialWheel.TOP).center(), TWO_PI), EPSILON);
            assertEquals(BOTTOM, halves.get(RadialWheel.BOTTOM).center(), EPSILON);
        }

        /** decision halves-arc-petals-at-rest */
        @Test
        void aTypeBelongsToTheHalfItsNamesFirstLetterFallsIn() {
            assertAll(Stream.of("aeon", "Mire", "molten").map(name -> (Executable) () ->
                    assertEquals(RadialWheel.TOP, RadialWheel.halfOfName(name), name)));
            assertAll(Stream.of("nether", "Vital", "zephyr").map(name -> (Executable) () ->
                    assertEquals(RadialWheel.BOTTOM, RadialWheel.halfOfName(name), name)));
        }

        @Test
        void openTypeIsReplacedByItsAbilitiesInTheOneRing() {
            RadialWheel wheel = opened(ABILITIES);
            List<RadialWheel.PetalArc> petals = wheel.layout();

            assertEquals(ABILITIES, abilityPetals(wheel).size());
            assertEquals(NEIGHBORS, typePetals(wheel).size());
            assertTrue(typePetals(wheel).stream().noneMatch(petal -> petal.type() == OPEN_TYPE));
            assertEquals(TWO_PI, petals.stream().mapToDouble(RadialWheel.PetalArc::arc).sum(), EPSILON);
            for (int i = 1; i < petals.size(); i++) {
                RadialWheel.PetalArc previous = petals.get(i - 1);
                assertEquals(previous.start() + previous.arc(), petals.get(i).start(), EPSILON);
            }
        }

        /** Asserts each neighbor either side of the open type at its arc by distance, nearest first, in degrees. */
        private static void assertNeighborArcs(List<RadialWheel.PetalArc> petals, double... degreesBySteps) {
            assertAll(IntStream.rangeClosed(1, degreesBySteps.length).boxed().flatMap(steps -> Stream.of(
                    (Executable) () -> assertEquals(Math.toRadians(degreesBySteps[steps - 1]),
                            typePetal(petals, stepsFrom(OPEN_TYPE, steps)).arc(), EPSILON, "cw " + steps),
                    () -> assertEquals(Math.toRadians(degreesBySteps[steps - 1]),
                            typePetal(petals, stepsFrom(OPEN_TYPE, -steps)).arc(), EPSILON, "ccw " + steps))));
        }

        /** decision fisheye-halves-its-max-and-floors-at-ten-degrees */
        @Test
        void neighborsStepEvenlyFromHalfTheOldNearestToTenDegreesAndOneAbilityTakesTheRest() {
            RadialWheel wheel = opened(1);

            assertNeighborArcs(wheel.layout(), 27.5, 24.0, 20.5, 17.0, 13.5, 10.0);
            assertEquals(Math.toRadians(135.0), abilityPetal(wheel, 0).arc(), EPSILON);
        }

        /** decision fisheye-halves-its-max-and-floors-at-ten-degrees */
        @Test
        void abilitiesShareWhatTheNeighborsLeaveEvenly() {
            RadialWheel wheel = opened(ABILITIES);
            double neighbors = typePetals(wheel).stream().mapToDouble(RadialWheel.PetalArc::arc).sum();

            assertEquals(Math.toRadians(225.0), neighbors, EPSILON);
            assertAll(abilityPetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals(Math.toRadians(135.0) / ABILITIES, petal.arc(), EPSILON)));
        }

        /** decision fisheye-halves-its-max-and-floors-at-ten-degrees */
        @Test
        void abilitiesShortOfThirtyDegreesFlattenTheStepsTowardTenFirst() {
            RadialWheel wheel = opened(SIX_ABILITIES);

            assertNeighborArcs(wheel.layout(), 20.0, 18.0, 16.0, 14.0, 12.0, 10.0);
            assertAll(abilityPetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals(RadialWheel.ABILITY_ARC, petal.arc(), EPSILON)));
        }

        /** decision fisheye-halves-its-max-and-floors-at-ten-degrees */
        @Test
        void moreAbilitiesThanFitHoldEveryNeighborAtTenDegreesAndShareTheRestEvenly() {
            RadialWheel wheel = opened(TWELVE_ABILITIES);

            assertNeighborArcs(wheel.layout(), 10.0, 10.0, 10.0, 10.0, 10.0, 10.0);
            assertAll(abilityPetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals(Math.toRadians(20.0), petal.arc(), EPSILON)));
            assertEquals(TWO_PI, wheel.layout().stream().mapToDouble(RadialWheel.PetalArc::arc).sum(), EPSILON);
        }

        /** decision open-type-keeps-thirteen-fading-to-twenty-percent */
        @Test
        void sixNeighborsASideStayFadingEvenlyToTwentyPercentAndTheRestHide() {
            RadialWheel wheel = opened(ABILITIES);
            List<RadialWheel.PetalArc> petals = wheel.layout();
            double[] presenceBySteps = {1.0, 0.84, 0.68, 0.52, 0.36, 0.2};

            assertAll(IntStream.rangeClosed(1, RadialWheel.NEIGHBORS_A_SIDE).boxed().flatMap(steps -> Stream.of(
                    (Executable) () -> assertEquals(presenceBySteps[steps - 1],
                            typePetal(petals, stepsFrom(OPEN_TYPE, steps)).presence(), EPSILON, "cw " + steps),
                    () -> assertEquals(presenceBySteps[steps - 1],
                            typePetal(petals, stepsFrom(OPEN_TYPE, -steps)).presence(), EPSILON, "ccw " + steps))));
            Set<Integer> shown = typePetals(wheel).stream().map(RadialWheel.PetalArc::type).collect(Collectors.toSet());
            assertEquals(NEIGHBORS, shown.size());
            assertAll(IntStream.rangeClosed(RadialWheel.NEIGHBORS_A_SIDE + 1, TYPES - RadialWheel.NEIGHBORS_A_SIDE - 1)
                    .mapToObj(steps -> (Executable) () -> assertFalse(shown.contains(stepsFrom(OPEN_TYPE, steps)),
                            "type " + steps + " steps away hides")));
        }

        /** decision open-type-keeps-thirteen-fading-to-twenty-percent */
        @Test
        void aWheelTheWindowHoldsWholeKeepsEveryTypeFull() {
            RadialWheel wheel = new RadialWheel(WINDOW_TYPES, type -> ABILITIES, type -> RadialWheel.TOP);
            hoverType(wheel, WINDOW_TYPES / 2);

            List<RadialWheel.PetalArc> others = typePetals(wheel);

            assertEquals(NEIGHBORS, others.size());
            assertAll(others.stream().map(petal -> (Executable)
                    () -> assertEquals(1.0, petal.presence(), EPSILON, "type " + petal.type())));
        }
    }

    /** The wheel shows only types the player holds with abilities to offer (decision wheel-shows-only-held-types-with-abilities). */
    @Nested
    class ShownTypes {

        private static final String NO_ABILITIES = "no_abilities";
        private static final String UNHELD = "unheld";
        private static final String ALL_LOCKED = "all_locked";
        private static final String ORDINARY = "ordinary";

        @Test
        void onlyHeldTypesOfferingAnAbilityShowLockedOnesIncluded() {
            Map<String, List<String>> offered = Map.of(NO_ABILITIES, List.of(), UNHELD, List.of("throw"),
                    ALL_LOCKED, List.of("locked"), ORDINARY, List.of("throw"));
            Map<String, Integer> held = Map.of(NO_ABILITIES, 10, UNHELD, 0, ALL_LOCKED, 10, ORDINARY, 10);

            List<String> shown = RadialWheel.shownTypes(List.of(NO_ABILITIES, UNHELD, ALL_LOCKED, ORDINARY),
                    offered::get, held::get);

            assertEquals(List.of(ALL_LOCKED, ORDINARY), shown);
        }

        @Test
        void aWheelOverNoTypesTakesEveryInputAndCancels() {
            RadialWheel wheel = new RadialWheel(0, type -> 0, type -> RadialWheel.TOP);

            moveTo(wheel, 0.0, PETAL);
            wheel.scroll(-1);
            wheel.tick();

            assertEquals(RadialWheel.Outcome.CANCEL, wheel.click());
        }
    }

    @Nested
    class Transitions {

        /** Asserts a half open: its types sharing the 270 degrees centered on its side, the other half on the 90 left. */
        private static void assertHalfOpen(RadialWheel wheel, int half, int firstType, int types) {
            double center = half == RadialWheel.TOP ? TOP : BOTTOM;
            List<RadialWheel.PetalArc> petals = wheel.layout();
            RadialWheel.HalfPetal held = wheel.halves().get(1 - half);

            assertEquals(half, wheel.openHalf());
            assertEquals(IntStream.range(firstType, firstType + types).boxed().toList(),
                    petals.stream().map(RadialWheel.PetalArc::type).toList());
            assertEquals(RadialWheel.HALF_OPEN_ARC, petals.stream().mapToDouble(RadialWheel.PetalArc::arc).sum(), EPSILON);
            assertEquals(center - RadialWheel.HALF_OPEN_ARC / 2, petals.getFirst().start(), EPSILON);
            assertAll(petals.stream().map(petal -> (Executable) () -> {
                double from = Math.IEEEremainder(petal.start() - center, TWO_PI);
                assertTrue(from >= -RadialWheel.HALF_OPEN_ARC / 2 - EPSILON
                        && from + petal.arc() <= RadialWheel.HALF_OPEN_ARC / 2 + EPSILON, petal + " leaves the 270");
            }));
            assertEquals(RadialWheel.HALF_OPEN_ARC, wheel.halves().get(half).arc(), EPSILON);
            assertEquals(RadialWheel.HALF_HELD_ARC, held.arc(), EPSILON);
            assertEquals(0.0, Math.IEEEremainder(held.center() - center - Math.PI, TWO_PI), EPSILON);
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void cursorOnTheTopHalfSpreadsItsTypesOver270DegreesCenteredOnTheTop() {
            RadialWheel wheel = wheelOverBothHalves();

            moveTo(wheel, TOP, BAND);

            assertHalfOpen(wheel, RadialWheel.TOP, 0, FIRST_BOTTOM);
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void cursorOnTheBottomHalfSpreadsItsTypesOver270DegreesCenteredOnTheBottom() {
            RadialWheel wheel = wheelOverBothHalves();

            moveTo(wheel, BOTTOM, BAND);

            assertHalfOpen(wheel, RadialWheel.BOTTOM, FIRST_BOTTOM, TYPES - FIRST_BOTTOM);
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void cursorOnTheHeldHalfsNinetyDegreesOpensThatHalf() {
            RadialWheel wheel = wheelOverBothHalves();
            moveTo(wheel, TOP, BAND);
            moveTo(wheel, typePetal(wheel.layout(), 2).center(), PETAL);

            moveTo(wheel, BOTTOM, PETAL);

            assertEquals(RadialWheel.NONE, wheel.selectedType());
            assertHalfOpen(wheel, RadialWheel.BOTTOM, FIRST_BOTTOM, TYPES - FIRST_BOTTOM);
        }

        /** decision halves-arc-petals-at-rest */
        @Test
        void cursorInTheHubFromAnOpenHalfReturnsToTheTwoHalves() {
            RadialWheel wheel = wheelOverBothHalves();
            moveTo(wheel, BOTTOM, BAND);

            moveTo(wheel, BOTTOM, HUB);

            assertEquals(RadialWheel.NONE, wheel.openHalf());
            assertTrue(wheel.layout().isEmpty());
            assertAll(wheel.halves().stream().map(half -> (Executable) () ->
                    assertEquals(Math.PI, half.arc(), EPSILON)));
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void aTypeOpenedInsideAHalfFansItsAbilitiesAndKeepsItsNeighborsInThatHalf() {
            RadialWheel wheel = wheelOverBothHalves();
            moveTo(wheel, TOP, BAND);

            moveTo(wheel, typePetal(wheel.layout(), FIRST_BOTTOM / 2).center(), PETAL);

            assertEquals(FIRST_BOTTOM / 2, wheel.selectedType());
            assertEquals(2, abilityPetals(wheel).size());
            assertTrue(typePetals(wheel).stream().allMatch(petal -> petal.type() < FIRST_BOTTOM),
                    "a type of the other half stands on the ring");
            assertEquals(RadialWheel.HALF_HELD_ARC, wheel.halves().get(RadialWheel.BOTTOM).arc(), EPSILON);
        }

        @Test
        void cursorOnATypesPetalAtRestOpensIt() {
            RadialWheel wheel = opened(ABILITIES);

            assertEquals(OPEN_TYPE, wheel.selectedType());
            assertTrue(wheel.isOpen());
        }

        @Test
        void cursorOnAnAbilityPetalHoversIt() {
            RadialWheel wheel = opened(ABILITIES);

            moveTo(wheel, abilityPetal(wheel, 1).center(), PETAL);

            assertEquals(1, wheel.hoveredAbility());
            assertEquals(OPEN_TYPE, wheel.selectedType());
        }

        @Test
        void cursorOnAShrunkenNeighborOpensIt() {
            RadialWheel wheel = opened(ABILITIES);
            moveTo(wheel, abilityPetal(wheel, 1).center(), PETAL);
            RadialWheel.PetalArc neighbor = typePetals(wheel).stream()
                    .filter(petal -> petal.type() == OPEN_TYPE + 1).findFirst().orElseThrow();

            moveTo(wheel, neighbor.center(), PETAL);

            assertEquals(OPEN_TYPE + 1, wheel.selectedType());
        }

        /** decision open-type-keeps-thirteen-fading-to-twenty-percent */
        @Test
        void openingAFarNeighborReformsTheNeighborsAroundIt() {
            RadialWheel wheel = opened(ABILITIES);
            // open-half-spans-270-degrees: the farthest neighbors turn behind the held half, the second shows
            int farthest = stepsFrom(OPEN_TYPE, 2);

            moveTo(wheel, typePetal(wheel.layout(), farthest).center(), PETAL);

            assertEquals(farthest, wheel.selectedType());
            Set<Integer> expected = IntStream.rangeClosed(1, RadialWheel.NEIGHBORS_A_SIDE).boxed()
                    .flatMap(steps -> Stream.of(stepsFrom(farthest, steps), stepsFrom(farthest, -steps)))
                    .collect(Collectors.toSet());
            assertEquals(expected, typePetals(wheel).stream().map(RadialWheel.PetalArc::type)
                    .collect(Collectors.toSet()));
        }

        @Test
        void cursorInTheHubReturnsTheWheelToRest() {
            RadialWheel wheel = opened(ABILITIES);
            moveTo(wheel, abilityPetal(wheel, 1).center(), PETAL);

            moveTo(wheel, abilityPetal(wheel, 1).center(), HUB);

            assertEquals(RadialWheel.NONE, wheel.selectedType());
            assertEquals(RadialWheel.NONE, wheel.openHalf());
            assertTrue(wheel.layout().isEmpty(), "the wheel returns to the two halves");
            assertEquals(Math.PI, wheel.halves().getFirst().arc(), EPSILON);
            assertEquals(RadialWheel.Outcome.CANCEL, wheel.click());
        }

        @Test
        void clickOnAnAbilitySelectsItOnTheGlove() {
            RadialWheel wheel = opened(ABILITIES);
            moveTo(wheel, abilityPetal(wheel, 2).center(), PETAL);

            RadialWheel.Outcome outcome = wheel.click();

            assertTrue(outcome.selects());
            assertEquals(new RadialWheel.Outcome(OPEN_TYPE, 2), outcome);
        }

        @Test
        void clickWithATypeOpenAndNoAbilityHoveredCancels() {
            RadialWheel wheel = opened(0);

            assertTrue(wheel.isOpen());
            assertEquals(RadialWheel.Outcome.CANCEL, wheel.click());
        }

        @Test
        void typeWithNoAbilitiesKeepsItsOwnPetalWhenOpen() {
            RadialWheel wheel = opened(0);

            assertEquals(OPEN_TYPE, wheel.selectedType());
            assertEquals(TYPES, typePetals(wheel).size());
            assertEquals(RadialWheel.Outcome.CANCEL, wheel.click());
        }
    }

    /**
     * Leaving the open type's abilities opens the adjacent type with its
     * abilities under the cursor, the ring turning the least it must and then
     * centering the landed ability on the cursor, and following the cursor
     * through a short grace window (decision ring-rotates-to-keep-the-cursor-inside).
     */
    @Nested
    class Rotation {

        private static final int WIDE_TYPE = 13;
        private static final int WIDE_ABILITIES = 6;
        private static final double FOLLOW = Math.toRadians(10.0);
        private static final double JUST_PAST = Math.toRadians(1.0);

        /** The wide type opens to six abilities, its neighbors to one, every other type to four. */
        private static RadialWheel wheelWithAWideType() {
            return new RadialWheel(TYPES, type -> type == WIDE_TYPE ? WIDE_ABILITIES
                    : Math.abs(type - WIDE_TYPE) == 1 ? 1 : ABILITIES, type -> RadialWheel.TOP);
        }

        private static RadialWheel wideTypeOpen() {
            RadialWheel wheel = wheelWithAWideType();
            hoverType(wheel, WIDE_TYPE);
            settle(wheel);
            return wheel;
        }

        private static void assertCursorInsideWithTheMargin(RadialWheel wheel, double cursor) {
            RadialWheel.PetalArc hovered = abilityPetal(wheel, wheel.hoveredAbility());
            double fromStart = wrap(cursor - hovered.start());
            double toEnd = wrap(hovered.start() + hovered.arc() - cursor);
            assertTrue(fromStart >= RadialWheel.CURSOR_MARGIN - EPSILON, "start margin " + fromStart);
            assertTrue(toEnd >= RadialWheel.CURSOR_MARGIN - EPSILON, "end margin " + toEnd);
        }

        private static void assertHoveredCenteredOn(RadialWheel wheel, double cursor) {
            assertEquals(0.0, Math.IEEEremainder(abilityPetal(wheel, wheel.hoveredAbility()).center() - cursor, TWO_PI),
                    EPSILON);
        }

        private static double wrap(double angle) {
            return ((angle % TWO_PI) + TWO_PI) % TWO_PI;
        }

        /** Where the open type's abilities start, as turned. */
        static double spanStart(RadialWheel wheel) {
            return abilityPetal(wheel, 0).start();
        }

        /** Where the open type's abilities end, as turned. */
        private static double spanEnd(RadialWheel wheel) {
            RadialWheel.PetalArc last = abilityPetal(wheel, abilityPetals(wheel).size() - 1);
            return last.start() + last.arc();
        }

        /** Where the open type's abilities start with the ring unturned: the layout's start less its turn. */
        private static double unturnedSpanStart(RadialWheel wheel) {
            return spanStart(wheel) - wheel.rotation();
        }

        @Test
        void wideTypeOpensTurnedTheLeastThatCentersItsLandedAbility() {
            RadialWheel wheel = wheelWithAWideType();
            moveTo(wheel, TOP, BAND);
            double cursor = typePetal(wheel.layout(), WIDE_TYPE).center();

            moveTo(wheel, cursor, PETAL);

            assertEquals(WIDE_TYPE, wheel.selectedType());
            assertTrue(wheel.hoveredAbility() != RadialWheel.NONE, "the cursor lands inside the centered abilities");
            assertTrue(Math.abs(wheel.rotation()) <= RadialWheel.ABILITY_ARC / 2 + EPSILON,
                    "the ring turns no further than centering the landed ability: " + wheel.rotation());
            assertHoveredCenteredOn(wheel, cursor);
        }

        @Test
        void leavingCounterclockwiseOpensThatNeighborUnderTheCursor() {
            RadialWheel wheel = wideTypeOpen();
            double cursor = spanStart(wheel) - JUST_PAST;

            moveTo(wheel, cursor, PETAL);

            assertEquals(WIDE_TYPE - 1, wheel.selectedType());
            assertEquals(0, wheel.hoveredAbility());
            assertCursorInsideWithTheMargin(wheel, cursor);
        }

        @Test
        void leavingClockwiseOpensThatNeighborUnderTheCursor() {
            RadialWheel wheel = wideTypeOpen();
            double cursor = spanEnd(wheel) + JUST_PAST;

            moveTo(wheel, cursor, PETAL);

            assertEquals(WIDE_TYPE + 1, wheel.selectedType());
            assertEquals(0, wheel.hoveredAbility());
            assertCursorInsideWithTheMargin(wheel, cursor);
        }

        @Test
        void ringTurnsTheLeastItMustThenCentersTheLandedAbility() {
            RadialWheel wheel = wideTypeOpen();
            double cursor = spanStart(wheel) - JUST_PAST;

            moveTo(wheel, cursor, PETAL);
            double unturned = unturnedSpanStart(wheel);

            // the solve's endpoint, cursor - span start - margin, plus the turn from the
            // landed ability's center (span start + solve + half an arc) to the cursor
            double solve = cursor - unturned - RadialWheel.CURSOR_MARGIN;
            double centering = cursor - (unturned + solve + abilityPetal(wheel, 0).arc() / 2);
            assertEquals(solve + centering, wheel.rotation(), EPSILON);
            assertHoveredCenteredOn(wheel, cursor);
        }

        @Test
        void insideTheGraceWindowTheRingFollowsTheCursor() {
            RadialWheel wheel = wideTypeOpen();
            double landed = spanStart(wheel) - JUST_PAST;
            moveTo(wheel, landed, PETAL);
            double cursor = landed - FOLLOW;

            moveTo(wheel, cursor, PETAL);

            assertEquals(WIDE_TYPE - 1, wheel.selectedType());
            assertEquals(0, wheel.hoveredAbility());
            assertHoveredCenteredOn(wheel, cursor);
        }

        @Test
        void afterTheGraceWindowTheSameMoveLeavesTheRingWhereItStands() {
            RadialWheel wheel = wideTypeOpen();
            double landed = spanStart(wheel) - JUST_PAST;
            moveTo(wheel, landed, PETAL);
            tick(wheel, RadialWheel.GRACE_TICKS);
            double turned = wheel.rotation();

            moveTo(wheel, landed - FOLLOW, PETAL);

            assertEquals(WIDE_TYPE - 1, wheel.selectedType());
            assertEquals(turned, wheel.rotation(), EPSILON);
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void theTurnedRingStaysInsideItsHalfAndTheHeldHalfStaysPinned() {
            RadialWheel wheel = wideTypeOpen();
            double cursor = spanStart(wheel) - JUST_PAST;

            moveTo(wheel, cursor, PETAL);
            settle(wheel);

            assertTrue(Math.abs(wheel.rotation()) > EPSILON, "the cursor turned the ring");
            assertEquals(RadialWheel.TOP, wheel.openHalf());
            RadialWheel.HalfPetal held = wheel.halves().get(RadialWheel.BOTTOM);
            assertEquals(RadialWheel.HALF_HELD_ARC, held.arc(), EPSILON);
            assertEquals(BOTTOM, held.center(), EPSILON);
            assertTrue(wheel.hiddenSector(0.0f).contains(BOTTOM), "the held half hides the ring's far side");
        }

        @Test
        void hubResetsTheRotationToRest() {
            RadialWheel wheel = wideTypeOpen();
            moveTo(wheel, spanStart(wheel) - JUST_PAST, PETAL);

            moveTo(wheel, 0.0, HUB);

            assertEquals(0.0, wheel.rotation(), EPSILON);
            assertFalse(wheel.isOpen());
        }
    }

    /**
     * Opening a type plays like a fan of cards: the type petal recedes
     * lengthwise and widens over the span, the full-size ability cards rotate
     * clockwise out from stacked behind it; closing plays it in reverse
     * (decisions petal-moves-animate, mid-animation-input-does-nothing).
     */
    @Nested
    class Ease {

        private static final int MID_TICKS = RingEase.DURATION_TICKS / 2;

        /** The top half opened and landed, then a type opened under the cursor, its fan just starting. */
        private static RadialWheel freshlyOpened() {
            RadialWheel wheel = wheel(ABILITIES);
            moveTo(wheel, TOP, BAND);
            settle(wheel);
            hoverType(wheel, OPEN_TYPE);
            return wheel;
        }

        private static RadialWheel.PetalArc petalOf(List<RadialWheel.PetalArc> petals, int type, int ability) {
            return petals.stream().filter(petal -> petal.type() == type && petal.ability() == ability)
                    .findFirst().orElseThrow();
        }

        private static List<RadialWheel.PetalArc> byStart(List<RadialWheel.PetalArc> petals) {
            return petals.stream().sorted(Comparator.comparingDouble(RadialWheel.PetalArc::start)).toList();
        }

        private static void assertSameLayout(List<RadialWheel.PetalArc> expected, List<RadialWheel.PetalArc> actual) {
            assertEquals(expected.size(), actual.size());
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).type(), actual.get(i).type());
                assertEquals(expected.get(i).ability(), actual.get(i).ability());
                assertEquals(expected.get(i).start(), actual.get(i).start(), EPSILON);
                assertEquals(expected.get(i).arc(), actual.get(i).arc(), EPSILON);
                assertEquals(expected.get(i).length(), actual.get(i).length(), EPSILON);
            }
        }

        /** Mid-fan: the type petal part-receded and part-widened on top, the cards fanned part way in order. */
        private static void assertMidFan(List<RadialWheel.PetalArc> midway, double targetCard) {
            RadialWheel.PetalArc own = petalOf(midway, OPEN_TYPE, RadialWheel.NONE);
            assertTrue(own.length() > RadialWheel.TYPE_BASE_LENGTH && own.length() < 1.0, "length " + own.length());
            assertTrue(own.arc() > HALF_TYPE_ARC && own.arc() < ABILITIES * targetCard, "arc " + own.arc());
            assertEquals(own, midway.get(midway.indexOf(petalOf(midway, OPEN_TYPE, 0)) + 1),
                    "the type petal draws right over its first card");
            double previous = own.start() - EPSILON;
            for (int ability = 0; ability < ABILITIES; ability++) {
                RadialWheel.PetalArc card = petalOf(midway, OPEN_TYPE, ability);
                assertEquals(targetCard, card.arc(), EPSILON);
                PetalMask.Petal base = own.shape();
                PetalMask.Petal cardShape = card.shape();
                for (double fraction : new double[]{0.0, 0.25, 0.5, 0.75}) {
                    double angle = card.start() + card.arc() * fraction;
                    assertEquals(base.reachOrLength(angle), cardShape.innerReach(angle), EPSILON,
                            "card " + ability + " starts where the type petal ends at " + fraction);
                    assertTrue(cardShape.innerReach(angle) > RadialWheel.HUB_FRACTION,
                            "card " + ability + " clear of the hub");
                }
                assertTrue(card.start() >= previous, "card " + ability + " fans clockwise");
                assertTrue(card.start() - own.start() <= ability * targetCard + EPSILON);
                assertTrue(ability == 0 || card.start() - own.start() < ability * targetCard,
                        "card " + ability + " short of its place");
                previous = card.start() + EPSILON;
            }
        }

        @Test
        void openingFansTheCardsOutFromBehindTheRecedingTypePetal() {
            RadialWheel wheel = freshlyOpened();
            double targetCard = petalOf(wheel.layout(), OPEN_TYPE, 0).arc();

            tick(wheel, MID_TICKS);

            assertMidFan(wheel.displayedLayout(0.0f), targetCard);
        }

        /** The displayed petals less the open type's base, which the cursor-facing layout leaves out. */
        private static List<RadialWheel.PetalArc> abilitiesAndOthers(List<RadialWheel.PetalArc> displayed) {
            return byStart(displayed.stream()
                    .filter(petal -> petal.isAbility() || petal.type() != OPEN_TYPE).toList());
        }

        /** decision open-type-keeps-thirteen-fading-to-twenty-percent */
        @Test
        void farNeighborsFadeAndFarTypesHideWithTheFan() {
            RadialWheel wheel = freshlyOpened();
            int farthest = stepsFrom(OPEN_TYPE, RadialWheel.NEIGHBORS_A_SIDE);
            int hidden = stepsFrom(OPEN_TYPE, TYPES / 2);

            tick(wheel, MID_TICKS);
            List<RadialWheel.PetalArc> midway = wheel.displayedLayout(0.0f);
            double fading = typePetal(midway, farthest).presence();
            double hiding = typePetal(midway, hidden).presence();
            tick(wheel, RingEase.DURATION_TICKS);
            List<RadialWheel.PetalArc> landed = wheel.displayedLayout(0.0f);

            assertTrue(fading > RadialWheel.NEIGHBOR_MIN_PRESENCE && fading < 1.0, "fading " + fading);
            assertTrue(hiding > 0.0 && hiding < 1.0, "hiding " + hiding);
            assertEquals(RadialWheel.NEIGHBOR_MIN_PRESENCE, typePetal(landed, farthest).presence(), EPSILON);
            assertTrue(landed.stream().noneMatch(petal -> petal.type() == hidden), "the hidden type has no petal");
        }

        /** decision fisheye-halves-its-max-and-floors-at-ten-degrees */
        @Test
        void aNeighborWalkReshapesTheFisheyeThroughTheEase() {
            RadialWheel wheel = opened(ABILITIES);
            int next = stepsFrom(OPEN_TYPE, 1);
            int growing = stepsFrom(OPEN_TYPE, 2);
            int shrinking = stepsFrom(OPEN_TYPE, -1);
            double growingBefore = typePetal(wheel.layout(), growing).arc();
            double shrinkingBefore = typePetal(wheel.layout(), shrinking).arc();

            moveTo(wheel, typePetal(wheel.layout(), next).center(), PETAL);
            double growingAfter = typePetal(wheel.layout(), growing).arc();
            double shrinkingAfter = typePetal(wheel.layout(), shrinking).arc();
            tick(wheel, MID_TICKS);
            List<RadialWheel.PetalArc> midway = wheel.displayedLayout(0.0f);
            double growingMid = typePetal(midway, growing).arc();
            double shrinkingMid = typePetal(midway, shrinking).arc();
            settle(wheel);

            assertEquals(next, wheel.selectedType());
            assertTrue(growingMid > growingBefore && growingMid < growingAfter, "growing " + growingMid);
            assertTrue(shrinkingMid < shrinkingBefore && shrinkingMid > shrinkingAfter, "shrinking " + shrinkingMid);
            double widest = typePetals(wheel).stream().mapToDouble(RadialWheel.PetalArc::arc).max().orElseThrow();
            assertEquals(widest, typePetal(wheel.layout(), stepsFrom(next, 1)).arc(), EPSILON);
            assertEquals(widest, typePetal(wheel.layout(), OPEN_TYPE).arc(), EPSILON);
        }

        @Test
        void easeLandsOnTheTargetAndHolds() {
            RadialWheel wheel = freshlyOpened();

            tick(wheel, RingEase.DURATION_TICKS);
            assertSameLayout(wheel.layout(), abilitiesAndOthers(wheel.displayedLayout(0.0f)));
            tick(wheel, RingEase.DURATION_TICKS);
            assertSameLayout(wheel.layout(), abilitiesAndOthers(wheel.displayedLayout(0.5f)));
        }

        @Test
        void landedTypePetalStandsAsTheShortWideBaseOfItsAbilities() {
            RadialWheel wheel = opened(ABILITIES);
            List<RadialWheel.PetalArc> abilities = abilityPetals(wheel);

            RadialWheel.PetalArc base = petalOf(wheel.displayedLayout(0.0f), OPEN_TYPE, RadialWheel.NONE);

            assertEquals(RadialWheel.TYPE_BASE_LENGTH, base.length(), EPSILON);
            assertEquals(abilities.getFirst().start(), base.start(), EPSILON);
            assertEquals(abilities.stream().mapToDouble(RadialWheel.PetalArc::arc).sum(), base.arc(), EPSILON);
            assertTrue(wheel.layout().stream().noneMatch(petal -> petal.type() == OPEN_TYPE && !petal.isAbility()));
        }

        @Test
        void closingPlaysTheFanInReverse() {
            RadialWheel wheel = opened(ABILITIES);
            double targetCard = petalOf(wheel.layout(), OPEN_TYPE, 0).arc();

            moveTo(wheel, 0.0, HUB);
            tick(wheel, MID_TICKS);

            assertMidFan(wheel.displayedLayout(0.0f), targetCard);
        }

        @Test
        void retargetMidEaseStartsFromTheDisplayedLayout() {
            RadialWheel wheel = freshlyOpened();
            tick(wheel, MID_TICKS);
            List<RadialWheel.PetalArc> before = wheel.displayedLayout(0.0f);

            while (wheel.selectedType() == OPEN_TYPE) {
                wheel.scroll(-1);
            }

            assertEquals(OPEN_TYPE + 1, wheel.selectedType());
            assertSameLayout(before, wheel.displayedLayout(0.0f));
            assertTrue(wheel.isAnimating());
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void hoveredAbilityGrowsToTheScreensEdgeAndEasesBackWhenLeft() {
            RadialWheel wheel = opened(ABILITIES);
            int first = wheel.hoveredAbility();
            assertEquals(RadialWheel.HOVER_REACH, petalOf(wheel.displayedLayout(0.0f), OPEN_TYPE, first).length(),
                    EPSILON);
            int other = (first + 1) % ABILITIES;
            assertEquals(1.0, petalOf(wheel.displayedLayout(0.0f), OPEN_TYPE, other).length(), EPSILON);

            moveTo(wheel, abilityPetal(wheel, other).center(), PETAL);
            wheel.tick();
            double midway = petalOf(wheel.displayedLayout(0.0f), OPEN_TYPE, first).length();
            assertTrue(midway > 1.0 && midway < RadialWheel.HOVER_REACH, "easing back " + midway);
            tick(wheel, RadialWheel.LIFT_TICKS);

            assertEquals(1.0, petalOf(wheel.displayedLayout(0.0f), OPEN_TYPE, first).length(), EPSILON);
            assertEquals(RadialWheel.HOVER_REACH, petalOf(wheel.displayedLayout(0.0f), OPEN_TYPE, other).length(),
                    EPSILON);
        }

        @Test
        void animatingHoldsUntilTheEaseLands() {
            RadialWheel wheel = wheel(ABILITIES);
            assertFalse(wheel.isAnimating());

            hoverType(wheel, OPEN_TYPE);
            tick(wheel, RingEase.DURATION_TICKS - 1);
            assertTrue(wheel.isAnimating());
            wheel.tick();
            assertFalse(wheel.isAnimating());
        }
    }

    /**
     * The scroll wheel steps through every ability of every type in list
     * order, so the radial works on a controller (decision abilities-replace-the-hovered-type).
     */
    @Nested
    class Scroll {

        private static final int EMPTY_TYPE = 1;

        /** Types 0 and 2 onward open to two abilities each; type 1 opens to none. */
        private static RadialWheel wheelWithAnEmptyType() {
            return new RadialWheel(TYPES, type -> type == EMPTY_TYPE ? 0 : 2, type -> RadialWheel.TOP);
        }

        /** The open type's abilities, centered on its half's middle. */
        private static void assertHovering(RadialWheel wheel, int type, int ability) {
            assertEquals(type, wheel.selectedType(), "type");
            assertEquals(ability, wheel.hoveredAbility(), "ability");
            List<RadialWheel.PetalArc> abilities = abilityPetals(wheel);
            double middle = (abilities.getFirst().start() + abilities.getLast().start() + abilities.getLast().arc()) / 2;
            double center = wheel.openHalf() == RadialWheel.TOP ? TOP : BOTTOM;
            assertEquals(0.0, Math.IEEEremainder(middle - center, TWO_PI), EPSILON, "abilities centered on the half");
        }

        @Test
        void scrollDownFromRestHoversTheFirstTypesFirstAbility() {
            RadialWheel wheel = wheelWithAnEmptyType();

            wheel.scroll(-1);

            assertHovering(wheel, 0, 0);
        }

        @Test
        void scrollUpFromRestHoversTheLastTypesLastAbility() {
            RadialWheel wheel = wheelWithAnEmptyType();

            wheel.scroll(1);

            assertHovering(wheel, TYPES - 1, 1);
        }

        @Test
        void scrollDownStepsThroughATypesAbilitiesThenOpensTheNextSkippingAnEmptyOne() {
            RadialWheel wheel = wheelWithAnEmptyType();
            wheel.scroll(-1);

            wheel.scroll(-1);
            assertHovering(wheel, 0, 1);
            wheel.scroll(-1);
            assertHovering(wheel, EMPTY_TYPE + 1, 0);
        }

        @Test
        void scrollUpFromATypesFirstAbilityOpensThePreviousOnItsLast() {
            RadialWheel wheel = wheelWithAnEmptyType();
            wheel.scroll(-1);
            wheel.scroll(-1);
            wheel.scroll(-1);

            wheel.scroll(1);

            assertHovering(wheel, 0, 1);
        }

        @Test
        void scrollDownFromTheLastTypesLastAbilityWrapsToTheFirst() {
            RadialWheel wheel = wheelWithAnEmptyType();
            wheel.scroll(1);

            wheel.scroll(-1);

            assertHovering(wheel, 0, 0);
        }

        @Test
        void scrollTurnsTheOpenTypesAbilitiesToTheMiddleOfItsHalfEvenAfterTheCursorTurnedIt() {
            RadialWheel wheel = Rotation.wheelWithAWideType();
            hoverType(wheel, Rotation.WIDE_TYPE);
            settle(wheel);
            moveTo(wheel, Rotation.spanStart(wheel) - Rotation.JUST_PAST, PETAL);
            int landed = wheel.selectedType();

            wheel.scroll(-1);

            assertHovering(wheel, landed + 1, 0);
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void scrollDownPastTheLastTopAbilityOpensTheBottomHalfOnItsFirstType() {
            RadialWheel wheel = wheelOverBothHalves();
            while (wheel.selectedType() != FIRST_BOTTOM - 1 || wheel.hoveredAbility() != 1) {
                wheel.scroll(-1);
            }
            assertEquals(RadialWheel.TOP, wheel.openHalf());

            wheel.scroll(-1);

            assertHovering(wheel, FIRST_BOTTOM, 0);
            assertEquals(RadialWheel.BOTTOM, wheel.openHalf());
            assertEquals(RadialWheel.HALF_OPEN_ARC, wheel.halves().get(RadialWheel.BOTTOM).arc(), EPSILON);
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void scrollDownPastTheLastBottomAbilityWrapsToTheTopHalfsFirstType() {
            RadialWheel wheel = wheelOverBothHalves();
            wheel.scroll(1);
            assertHovering(wheel, TYPES - 1, 1);
            assertEquals(RadialWheel.BOTTOM, wheel.openHalf());

            wheel.scroll(-1);

            assertHovering(wheel, 0, 0);
            assertEquals(RadialWheel.TOP, wheel.openHalf());
        }
    }
}
