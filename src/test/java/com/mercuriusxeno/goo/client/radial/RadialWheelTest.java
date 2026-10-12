package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
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
        tick(wheel, RingEase.DURATION_TICKS);
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
            assertEquals(TYPES - 1, typePetals(wheel).size());
            assertTrue(typePetals(wheel).stream().noneMatch(petal -> petal.type() == OPEN_TYPE));
            for (int i = 1; i < petals.size(); i++) {
                RadialWheel.PetalArc previous = petals.get(i - 1);
                assertEquals(previous.start() + previous.arc(), petals.get(i).start(), EPSILON);
            }
        }

        /** A half of thirteen types, the shipped size, every type opening to the same abilities, one open. */
        private static RadialWheel halfOfThirteen(int abilities) {
            RadialWheel wheel = new RadialWheel(WINDOW_TYPES, type -> abilities, type -> RadialWheel.TOP);
            hoverType(wheel, WINDOW_TYPES / 2);
            settle(wheel);
            return wheel;
        }

        /**
         * Asserts the ring full, no type petal wider than its rest share, no
         * ability wider than 30 degrees, and the neighbors narrowing, or
         * holding, with distance either way down to no less than 10 degrees.
         */
        private static void assertFilledAndNoWiderThanAtRest(RadialWheel wheel, int types) {
            int open = wheel.selectedType();
            double rest = TWO_PI / types;
            List<RadialWheel.PetalArc> petals = wheel.layout();
            assertEquals(TWO_PI, petals.stream().mapToDouble(RadialWheel.PetalArc::arc).sum(), EPSILON, "ring full");
            assertAll(petals.stream().map(petal -> (Executable) () -> {
                if (petal.isAbility()) {
                    assertTrue(petal.arc() <= Math.max(RadialWheel.ABILITY_ARC, rest) + EPSILON, petal + " too wide");
                } else {
                    assertTrue(petal.arc() <= rest + EPSILON, petal + " wider than at rest");
                    assertTrue(petal.arc() >= Math.min(RadialWheel.NEIGHBOR_MIN_ARC, rest) - EPSILON, petal + " too narrow");
                }
            }));
            for (int steps = 1; steps < types / 2; steps++) {
                int cw = Math.floorMod(open + steps, types);
                int farther = Math.floorMod(open + steps + 1, types);
                assertTrue(typePetal(petals, cw).arc() >= typePetal(petals, farther).arc() - EPSILON,
                        "step " + steps + " narrower than the next");
            }
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void anOpenHalfsTypesFillTheWholeRingEvenly() {
            RadialWheel wheel = wheelOverBothHalves();

            moveTo(wheel, TOP, BAND);

            assertAll(wheel.layout().stream().map(petal -> (Executable) () ->
                    assertEquals(TWO_PI / FIRST_BOTTOM, petal.arc(), EPSILON, petal.toString())));
            assertEquals(TWO_PI, wheel.layout().stream().mapToDouble(RadialWheel.PetalArc::arc).sum(), EPSILON);
        }

        /** decision fisheye-halves-its-max-and-floors-at-ten-degrees */
        @Test
        void anOpenTypeFillsTheRingWithNoPetalWiderThanAtRest() {
            assertAll(IntStream.of(1, ABILITIES, SIX_ABILITIES, TWELVE_ABILITIES).mapToObj(abilities ->
                    (Executable) () -> assertFilledAndNoWiderThanAtRest(halfOfThirteen(abilities), WINDOW_TYPES)));
            assertFilledAndNoWiderThanAtRest(opened(ABILITIES), TYPES);
        }

        /** decision fisheye-halves-its-max-and-floors-at-ten-degrees */
        @Test
        void abilitiesOutgrowingTheirRestSlotTakeThirtyDegreesAndSqueezeTheNeighborsFarthestMost() {
            RadialWheel wheel = halfOfThirteen(ABILITIES);
            int open = wheel.selectedType();
            List<RadialWheel.PetalArc> petals = wheel.layout();

            assertAll(abilityPetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals(RadialWheel.ABILITY_ARC, petal.arc(), EPSILON)));
            double nearest = typePetal(petals, open + 1).arc();
            double farthest = typePetal(petals, open + WINDOW_TYPES / 2).arc();
            assertTrue(nearest < TWO_PI / WINDOW_TYPES && farthest < nearest, nearest + " then " + farthest);
            assertEquals(nearest, typePetal(petals, open - 1).arc(), EPSILON, "the squeeze matches either side");
        }

        /** decision fisheye-halves-its-max-and-floors-at-ten-degrees */
        @Test
        void abilitiesFittingTheirRestSlotShareItAndLeaveTheNeighborsAtRest() {
            int types = 4;
            RadialWheel wheel = new RadialWheel(types, type -> 2, type -> RadialWheel.TOP);
            hoverType(wheel, 1);

            assertAll(abilityPetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals(TWO_PI / types / 2, petal.arc(), EPSILON)));
            assertAll(typePetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals(TWO_PI / types, petal.arc(), EPSILON)));
        }

        /** decision fisheye-halves-its-max-and-floors-at-ten-degrees */
        @Test
        void moreAbilitiesThanFitHoldEveryNeighborAtTenDegreesAndShareTheRestEvenly() {
            RadialWheel wheel = halfOfThirteen(TWELVE_ABILITIES);

            assertAll(typePetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals(RadialWheel.NEIGHBOR_MIN_ARC, petal.arc(), EPSILON)));
            assertAll(abilityPetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals(Math.toRadians(20.0), petal.arc(), EPSILON)));
        }

        /** decision open-type-keeps-thirteen-fading-to-twenty-percent */
        @Test
        void sixNeighborsASideFadeEvenlyToTwentyPercentAndTheRestStayAtTwenty() {
            RadialWheel wheel = opened(ABILITIES);
            List<RadialWheel.PetalArc> petals = wheel.layout();
            double[] presenceBySteps = {1.0, 0.84, 0.68, 0.52, 0.36, 0.2};

            assertAll(IntStream.rangeClosed(1, RadialWheel.NEIGHBORS_A_SIDE).boxed().flatMap(steps -> Stream.of(
                    (Executable) () -> assertEquals(presenceBySteps[steps - 1],
                            typePetal(petals, stepsFrom(OPEN_TYPE, steps)).presence(), EPSILON, "cw " + steps),
                    () -> assertEquals(presenceBySteps[steps - 1],
                            typePetal(petals, stepsFrom(OPEN_TYPE, -steps)).presence(), EPSILON, "ccw " + steps))));
            assertAll(IntStream.rangeClosed(RadialWheel.NEIGHBORS_A_SIDE + 1, TYPES - RadialWheel.NEIGHBORS_A_SIDE - 1)
                    .mapToObj(steps -> (Executable) () -> assertEquals(RadialWheel.NEIGHBOR_MIN_PRESENCE,
                            typePetal(petals, stepsFrom(OPEN_TYPE, steps)).presence(), EPSILON, "type " + steps)));
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

        /**
         * Asserts a half open: its types in list order filling the whole ring
         * evenly, centered on its side, its half petal at 270 degrees and the
         * other half petal on the 90 left.
         */
        private static void assertHalfOpen(RadialWheel wheel, int half, int firstType, int types) {
            double center = half == RadialWheel.TOP ? TOP : BOTTOM;
            List<RadialWheel.PetalArc> petals = wheel.layout();
            RadialWheel.HalfPetal held = wheel.halves().get(1 - half);

            assertEquals(half, wheel.openHalf());
            assertEquals(IntStream.range(firstType, firstType + types).boxed().toList(),
                    petals.stream().map(RadialWheel.PetalArc::type).toList());
            assertEquals(TWO_PI, petals.stream().mapToDouble(RadialWheel.PetalArc::arc).sum(), EPSILON);
            assertEquals(0.0, Math.IEEEremainder(petals.getFirst().start() - (center - Math.PI), TWO_PI), EPSILON);
            assertEquals(RadialWheel.HALF_OPEN_ARC, wheel.halves().get(half).arc(), EPSILON);
            assertEquals(RadialWheel.HALF_HELD_ARC, held.arc(), EPSILON);
            assertEquals(0.0, Math.IEEEremainder(held.center() - center - Math.PI, TWO_PI), EPSILON);
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void cursorOnTheTopHalfFillsTheRingWithItsTypesCenteredOnTheTop() {
            RadialWheel wheel = wheelOverBothHalves();

            moveTo(wheel, TOP, BAND);

            assertHalfOpen(wheel, RadialWheel.TOP, 0, FIRST_BOTTOM);
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void cursorOnTheBottomHalfFillsTheRingWithItsTypesCenteredOnTheBottom() {
            RadialWheel wheel = wheelOverBothHalves();

            moveTo(wheel, BOTTOM, BAND);

            assertHalfOpen(wheel, RadialWheel.BOTTOM, FIRST_BOTTOM, TYPES - FIRST_BOTTOM);
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void cursorOnTheHeldHalfPetalOpensThatHalf() {
            RadialWheel wheel = wheelOverBothHalves();
            moveTo(wheel, TOP, BAND);
            moveTo(wheel, typePetal(wheel.layout(), 2).center(), PETAL);

            moveTo(wheel, BOTTOM, BAND);

            assertEquals(RadialWheel.NONE, wheel.selectedType());
            assertHalfOpen(wheel, RadialWheel.BOTTOM, FIRST_BOTTOM, TYPES - FIRST_BOTTOM);
        }

        /** decision open-half-spans-270-degrees: the held half's petal is its own hit region, the ring stays whole */
        @Test
        void cursorOnTheRingOutsideTheHeldHalfsPetalStillOpensTheNeighborThere() {
            RadialWheel wheel = opened(ABILITIES);
            RadialWheel.PetalArc overHeld = typePetals(wheel).stream()
                    .filter(petal -> Math.abs(Math.IEEEremainder(petal.center() - BOTTOM, TWO_PI))
                            < RadialWheel.HALF_HELD_ARC / 2)
                    .findFirst().orElseThrow();

            moveTo(wheel, overHeld.center(), PETAL);

            assertEquals(RadialWheel.TOP, wheel.openHalf());
            assertEquals(overHeld.type(), wheel.selectedType());
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
        void openingTheFarthestNeighborReformsTheFadeAroundIt() {
            RadialWheel wheel = opened(ABILITIES);
            int farthest = stepsFrom(OPEN_TYPE, RadialWheel.NEIGHBORS_A_SIDE);

            moveTo(wheel, typePetal(wheel.layout(), farthest).center(), PETAL);

            assertEquals(farthest, wheel.selectedType());
            List<RadialWheel.PetalArc> petals = wheel.layout();
            assertEquals(TYPES - 1, typePetals(wheel).size());
            assertEquals(1.0, typePetal(petals, stepsFrom(farthest, 1)).presence(), EPSILON);
            assertEquals(RadialWheel.NEIGHBOR_MIN_PRESENCE, typePetal(petals, OPEN_TYPE).presence(), EPSILON);
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
     * The ring rests with the open type's abilities centered on its half and
     * turns only to put an ability's middle under the cursor: opening a type
     * lands the cursor in the ability the least turn from rest brings there,
     * and crossing between abilities springs the ring back toward rest
     * whenever some ability can sit under the cursor with less turn, never
     * winding it further (decision ring-rotates-to-keep-the-cursor-inside).
     */
    @Nested
    class Rotation {

        private static final int WIDE_TYPE = 13;
        private static final int WIDE_ABILITIES = 6;
        private static final double JUST_PAST = Math.toRadians(1.0);
        /** A neighbor far enough round that opening it turns the ring well away from rest. */
        private static final int FAR = 3;
        private static final double SWEEP_STEP = Math.toRadians(1.0);
        private static final int SWEEP_STEPS = 90;

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

        private static void assertHoveredCenteredOn(RadialWheel wheel, double cursor) {
            assertEquals(0.0, Math.IEEEremainder(abilityPetal(wheel, wheel.hoveredAbility()).center() - cursor, TWO_PI),
                    EPSILON);
        }

        /** Asserts no ability of the open type could sit under the cursor with less turn from rest than the ring stands at. */
        private static void assertLandedNearestRest(RadialWheel wheel, double cursor) {
            double turned = wheel.rotation();
            assertAll(abilityPetals(wheel).stream().map(petal -> (Executable) () -> {
                double unturned = petal.center() - turned;
                assertTrue(Math.abs(turned) <= Math.abs(Math.IEEEremainder(cursor - unturned, TWO_PI)) + EPSILON,
                        "ability " + petal.ability() + " lands with less turn than " + turned);
            }));
        }

        /** Where the open type's abilities start, as turned. */
        static double spanStart(RadialWheel wheel) {
            return abilityPetal(wheel, 0).start();
        }

        /** The wide type open, then the cursor onto a far neighbor, which opens turned well away from rest. */
        private static RadialWheel farNeighborOpen() {
            RadialWheel wheel = wideTypeOpen();
            moveTo(wheel, typePetal(wheel.layout(), WIDE_TYPE + FAR).center(), PETAL);
            settle(wheel);
            return wheel;
        }

        @Test
        void openingATypeLandsTheCursorInTheAbilityTheLeastTurnFromRestBringsThere() {
            RadialWheel wheel = wideTypeOpen();
            double cursor = typePetal(wheel.layout(), WIDE_TYPE + FAR).center();

            moveTo(wheel, cursor, PETAL);

            assertEquals(WIDE_TYPE + FAR, wheel.selectedType());
            assertTrue(Math.abs(wheel.rotation()) > RadialWheel.ABILITY_ARC, "the ring turns well away from rest");
            assertHoveredCenteredOn(wheel, cursor);
            assertLandedNearestRest(wheel, cursor);
        }

        /** The cursor just across the hovered ability's edge one way, onto the next ability that way. */
        private static double justAcross(RadialWheel wheel, double direction) {
            RadialWheel.PetalArc hovered = abilityPetal(wheel, wheel.hoveredAbility());
            return hovered.center() + direction * (hovered.arc() / 2 + JUST_PAST);
        }

        @Test
        void crossingWhereCenteringTheNextAbilityRelievesTensionSpringsItUnderTheCursor() {
            RadialWheel wheel = farNeighborOpen();
            double tension = wheel.rotation();
            int crossed = wheel.hoveredAbility();
            moveTo(wheel, justAcross(wheel, -Math.signum(tension)), PETAL);
            double cursor = justAcross(wheel, Math.signum(tension));

            moveTo(wheel, cursor, PETAL);

            assertEquals(crossed, wheel.hoveredAbility());
            assertTrue(Math.abs(wheel.rotation()) < Math.abs(tension), wheel.rotation() + " from " + tension);
            assertHoveredCenteredOn(wheel, cursor);
            assertTrue(wheel.isAnimating(), "the ring springs, easing");
        }

        @Test
        void crossingWhereCenteringTheNextAbilityWouldAddTensionLeavesTheRingWhereItStands() {
            RadialWheel wheel = farNeighborOpen();
            double tension = wheel.rotation();
            int crossed = wheel.hoveredAbility() - (int) Math.signum(tension);

            moveTo(wheel, justAcross(wheel, -Math.signum(tension)), PETAL);

            assertEquals(crossed, wheel.hoveredAbility());
            assertEquals(tension, wheel.rotation(), EPSILON);
        }

        @Test
        void sweepingTheCursorEitherWayAcrossTheAbilitiesNeverWindsTheRingFurtherFromRest() {
            RadialWheel wheel = farNeighborOpen();
            int open = wheel.selectedType();
            double cursor = abilityPetal(wheel, wheel.hoveredAbility()).center();

            for (double direction : new double[]{1.0, -1.0, 1.0}) {
                for (int step = 0; step < SWEEP_STEPS; step++) {
                    double next = cursor + direction * SWEEP_STEP;
                    RadialWheel.PetalArc under = RadialWheel.petalAt(wheel.layout(), next);
                    if (!under.isAbility()) {
                        break;
                    }
                    double before = Math.abs(wheel.rotation());
                    moveTo(wheel, next, PETAL);
                    cursor = next;
                    assertEquals(open, wheel.selectedType());
                    assertTrue(Math.abs(wheel.rotation()) <= before + EPSILON,
                            "wound from " + before + " to " + wheel.rotation());
                }
            }
        }

        /** Sweeps the cursor one degree at a time one way across the open type's abilities, answering each it hovered. */
        private static List<Integer> sweepAcross(RadialWheel wheel, double direction) {
            List<Integer> hovered = new java.util.ArrayList<>(List.of(wheel.hoveredAbility()));
            double cursor = abilityPetal(wheel, wheel.hoveredAbility()).center();
            for (int step = 0; step < SWEEP_STEPS; step++) {
                double next = cursor + direction * SWEEP_STEP;
                if (!RadialWheel.petalAt(wheel.layout(), next).isAbility()) {
                    break;
                }
                moveTo(wheel, next, PETAL);
                cursor = next;
                if (wheel.hoveredAbility() != hovered.getLast()) {
                    hovered.add(wheel.hoveredAbility());
                }
            }
            return hovered;
        }

        @Test
        void sweepingEitherWayWhileTurnedReachesEveryAbilityThatWayInTurn() {
            assertAll(DoubleStream.of(1.0, -1.0).mapToObj(direction -> (Executable) () -> {
                RadialWheel wheel = farNeighborOpen();
                int from = wheel.hoveredAbility();
                int last = direction > 0 ? abilityPetals(wheel).size() - 1 : 0;

                List<Integer> hovered = sweepAcross(wheel, direction);

                assertEquals(IntStream.iterate(from, ability -> ability + (int) direction)
                        .limit(Math.abs(last - from) + 1).boxed().toList(), hovered, "sweeping " + direction);
            }));
        }

        @Test
        void crossingAbilitiesAtRestLeavesTheRingUnturned() {
            RadialWheel wheel = new RadialWheel(TYPES, type -> ABILITIES, type -> RadialWheel.TOP);
            wheel.scroll(-1);
            settle(wheel);
            moveTo(wheel, TOP, BAND);
            moveTo(wheel, abilityPetal(wheel, 1).center(), PETAL);

            moveTo(wheel, abilityPetal(wheel, 2).center(), PETAL);

            assertEquals(2, wheel.hoveredAbility());
            assertEquals(0.0, wheel.rotation(), EPSILON);
        }

        /** decision open-half-spans-270-degrees */
        @Test
        void theRingTurnsWhileTheHeldHalfStaysPinned() {
            RadialWheel wheel = farNeighborOpen();

            assertTrue(Math.abs(wheel.rotation()) > EPSILON, "the cursor turned the ring");
            assertEquals(RadialWheel.TOP, wheel.openHalf());
            RadialWheel.HalfPetal held = wheel.halves().get(RadialWheel.BOTTOM);
            assertEquals(RadialWheel.HALF_HELD_ARC, held.arc(), EPSILON);
            assertEquals(BOTTOM, held.center(), EPSILON);
        }

        @Test
        void hubResetsTheRotationToRest() {
            RadialWheel wheel = farNeighborOpen();

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
        void farNeighborsFadeWithTheFan() {
            RadialWheel wheel = freshlyOpened();
            int farthest = stepsFrom(OPEN_TYPE, RadialWheel.NEIGHBORS_A_SIDE);
            int across = stepsFrom(OPEN_TYPE, TYPES / 2);

            tick(wheel, MID_TICKS);
            List<RadialWheel.PetalArc> midway = wheel.displayedLayout(0.0f);
            double fading = typePetal(midway, farthest).presence();
            double fadingAcross = typePetal(midway, across).presence();
            tick(wheel, RingEase.DURATION_TICKS);
            List<RadialWheel.PetalArc> landed = wheel.displayedLayout(0.0f);

            assertTrue(fading > RadialWheel.NEIGHBOR_MIN_PRESENCE && fading < 1.0, "fading " + fading);
            assertTrue(fadingAcross > RadialWheel.NEIGHBOR_MIN_PRESENCE && fadingAcross < 1.0, "across " + fadingAcross);
            assertEquals(RadialWheel.NEIGHBOR_MIN_PRESENCE, typePetal(landed, farthest).presence(), EPSILON);
            assertEquals(RadialWheel.NEIGHBOR_MIN_PRESENCE, typePetal(landed, across).presence(), EPSILON);
        }

        /** decision fisheye-halves-its-max-and-floors-at-ten-degrees */
        @Test
        void aNeighborWalkReshapesTheFisheyeThroughTheEase() {
            RadialWheel wheel = Layout.halfOfThirteen(ABILITIES);
            int open = wheel.selectedType();
            int next = open + 1;
            int growing = open + 2;
            int shrinking = open - 1;
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
            assertEquals(widest, typePetal(wheel.layout(), next + 1).arc(), EPSILON);
            assertEquals(widest, typePetal(wheel.layout(), open).arc(), EPSILON);
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
