package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the one-ring radial wheel's layout and its state transitions from cursor, scroll and click events (decisions abilities-replace-the-hovered-type, ability-petals-take-a-type-arc-to-a-floor). */
class RadialWheelTest {

    private static final int TYPES = 16;
    private static final int OPEN_TYPE = 3;
    private static final int ABILITIES = 4;
    private static final int MANY_ABILITIES = 10;
    private static final double RADIUS = 100.0;
    private static final double PETAL = (RadialWheel.HUB_FRACTION + 1.0) / 2 * RADIUS;
    private static final double HUB = RadialWheel.HUB_FRACTION / 2 * RADIUS;
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double TYPE_ARC = TWO_PI / TYPES;
    private static final double EPSILON = 1e-9;

    private static RadialWheel wheel(int abilities) {
        return new RadialWheel(TYPES, type -> abilities);
    }

    /** Moves the cursor to a clockwise angle from the top at a distance from the center. */
    private static void moveTo(RadialWheel wheel, double angle, double distance) {
        wheel.moveCursor(Math.sin(angle) * distance, -Math.cos(angle) * distance, RADIUS);
    }

    private static RadialWheel opened(int abilities) {
        RadialWheel wheel = wheel(abilities);
        moveTo(wheel, (OPEN_TYPE + 0.5) * TYPE_ARC, PETAL);
        return wheel;
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

    @Nested
    class Layout {

        @Test
        void radiusIsNinetyFivePercentOfTheSmallerDimensionHalved() {
            assertEquals(513, RadialWheel.outerRadius(1920, 1080), EPSILON);
            assertEquals(380, RadialWheel.outerRadius(800, 1200), EPSILON);
        }

        @Test
        void restHoldsOneFullTypeArcPerType() {
            List<RadialWheel.PetalArc> petals = wheel(ABILITIES).layout();

            assertEquals(TYPES, petals.size());
            assertAll(petals.stream().map(petal -> (Executable) () -> {
                assertFalse(petal.isAbility());
                assertEquals(TYPE_ARC, petal.arc(), EPSILON);
                assertEquals(petal.type() * TYPE_ARC, petal.start(), EPSILON);
            }));
        }

        @Test
        void openTypeIsReplacedByItsAbilitiesInTheOneRing() {
            RadialWheel wheel = opened(ABILITIES);
            List<RadialWheel.PetalArc> petals = wheel.layout();

            assertEquals(ABILITIES, abilityPetals(wheel).size());
            assertEquals(TYPES - 1, typePetals(wheel).size());
            assertTrue(typePetals(wheel).stream().noneMatch(petal -> petal.type() == OPEN_TYPE));
            assertEquals(TWO_PI, petals.stream().mapToDouble(RadialWheel.PetalArc::arc).sum(), EPSILON);
            for (int i = 1; i < petals.size(); i++) {
                RadialWheel.PetalArc previous = petals.get(i - 1);
                assertEquals(previous.start() + previous.arc(), petals.get(i).start(), EPSILON);
            }
        }

        @Test
        void abilitiesTakeAFullTypeArcWhileTheOthersHoldTheFloor() {
            RadialWheel wheel = opened(ABILITIES);

            assertAll(abilityPetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals(TYPE_ARC, petal.arc(), EPSILON)));
            assertAll(typePetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals((TWO_PI - ABILITIES * TYPE_ARC) / (TYPES - 1), petal.arc(), EPSILON)));
        }

        @Test
        void pastTheFloorTheOthersHoldTenDegreesAndTheAbilitiesSplitTheRest() {
            RadialWheel wheel = opened(MANY_ABILITIES);
            double floor = Math.toRadians(10.0);

            assertAll(typePetals(wheel).stream().map(petal -> (Executable) () -> assertEquals(floor, petal.arc(), EPSILON)));
            assertAll(abilityPetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals((TWO_PI - (TYPES - 1) * floor) / MANY_ABILITIES, petal.arc(), EPSILON)));
        }
    }

    @Nested
    class Transitions {

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

        @Test
        void cursorInTheHubReturnsTheWheelToRest() {
            RadialWheel wheel = opened(ABILITIES);
            moveTo(wheel, abilityPetal(wheel, 1).center(), PETAL);

            moveTo(wheel, abilityPetal(wheel, 1).center(), HUB);

            assertEquals(RadialWheel.NONE, wheel.selectedType());
            assertEquals(TYPES, wheel.layout().size());
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
            RadialWheel wheel = wheel(ABILITIES);
            wheel.scroll(-1);

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
     * abilities under the cursor, the ring turning the least it must
     * (decision ring-rotates-to-keep-the-cursor-inside).
     */
    @Nested
    class Rotation {

        private static final int WIDE_TYPE = 3;
        private static final int WIDE_ABILITIES = 6;
        /** The wide type's abilities start here unturned: three shrunken 15 degree petals before it. */
        private static final double WIDE_SPAN_START = Math.toRadians(45.0);
        /** And end here: six abilities of a full 22.5 degree type arc each. */
        private static final double WIDE_SPAN_END = Math.toRadians(180.0);
        private static final double JUST_PAST = Math.toRadians(1.0);

        /** The wide type opens to six abilities, its neighbors to one, every other type to four. */
        private static RadialWheel wheelWithAWideType() {
            return new RadialWheel(TYPES, type -> type == WIDE_TYPE ? WIDE_ABILITIES
                    : Math.abs(type - WIDE_TYPE) == 1 ? 1 : ABILITIES);
        }

        private static RadialWheel wideTypeOpen() {
            RadialWheel wheel = wheelWithAWideType();
            moveTo(wheel, (WIDE_TYPE + 0.5) * TYPE_ARC, PETAL);
            return wheel;
        }

        private static void assertCursorInsideWithTheMargin(RadialWheel wheel, double cursor) {
            RadialWheel.PetalArc hovered = abilityPetal(wheel, wheel.hoveredAbility());
            double fromStart = wrap(cursor - hovered.start());
            double toEnd = wrap(hovered.start() + hovered.arc() - cursor);
            assertTrue(fromStart >= RadialWheel.CURSOR_MARGIN - EPSILON, "start margin " + fromStart);
            assertTrue(toEnd >= RadialWheel.CURSOR_MARGIN - EPSILON, "end margin " + toEnd);
        }

        private static double wrap(double angle) {
            return ((angle % TWO_PI) + TWO_PI) % TWO_PI;
        }

        @Test
        void wideTypeOpensUnturnedSinceItsSpanHoldsTheCursor() {
            RadialWheel wheel = wideTypeOpen();

            assertEquals(WIDE_TYPE, wheel.selectedType());
            assertEquals(WIDE_SPAN_START, abilityPetal(wheel, 0).start(), EPSILON);
            assertEquals(0.0, wheel.rotation(), EPSILON);
        }

        @Test
        void leavingCounterclockwiseOpensThatNeighborUnderTheCursor() {
            RadialWheel wheel = wideTypeOpen();
            double cursor = WIDE_SPAN_START - JUST_PAST;

            moveTo(wheel, cursor, PETAL);

            assertEquals(WIDE_TYPE - 1, wheel.selectedType());
            assertEquals(0, wheel.hoveredAbility());
            assertCursorInsideWithTheMargin(wheel, cursor);
        }

        @Test
        void leavingClockwiseOpensThatNeighborUnderTheCursor() {
            RadialWheel wheel = wideTypeOpen();
            double cursor = WIDE_SPAN_END + JUST_PAST;

            moveTo(wheel, cursor, PETAL);

            assertEquals(WIDE_TYPE + 1, wheel.selectedType());
            assertEquals(0, wheel.hoveredAbility());
            assertCursorInsideWithTheMargin(wheel, cursor);
        }

        @Test
        void ringTurnsByTheMarginShortfallAndNoMore() {
            RadialWheel wheel = wideTypeOpen();
            double cursor = WIDE_SPAN_START - JUST_PAST;

            moveTo(wheel, cursor, PETAL);

            // the one-ability neighbor's span starts unturned where the wide type's did
            assertEquals(cursor - WIDE_SPAN_START - RadialWheel.CURSOR_MARGIN, wheel.rotation(), EPSILON);
        }

        @Test
        void hubResetsTheRotationToRest() {
            RadialWheel wheel = wideTypeOpen();
            moveTo(wheel, WIDE_SPAN_START - JUST_PAST, PETAL);

            moveTo(wheel, 0.0, HUB);

            assertEquals(0.0, wheel.rotation(), EPSILON);
            assertFalse(wheel.isOpen());
        }

        @Test
        void scrollOpensTheNextTypeUnderTheCursor() {
            RadialWheel wheel = wideTypeOpen();
            double cursor = (WIDE_TYPE + 0.5) * TYPE_ARC;

            wheel.scroll(-1);

            assertEquals(WIDE_TYPE + 1, wheel.selectedType());
            assertEquals(0, wheel.hoveredAbility());
            assertCursorInsideWithTheMargin(wheel, cursor);
        }
    }

    /**
     * The picture eases toward the target over a short duration with the
     * cursor inside the open type throughout, and reads as animating until it
     * lands (decisions petal-moves-animate, mid-animation-input-does-nothing).
     */
    @Nested
    class Ease {

        private static final float[] PARTIALS = {0.0f, 0.25f, 0.5f, 0.75f};
        private static final int MID_TICKS = RingEase.DURATION_TICKS / 2;

        private static RadialWheel.PetalArc petalOf(List<RadialWheel.PetalArc> petals, int type, int ability) {
            return petals.stream().filter(petal -> petal.type() == type && petal.ability() == ability)
                    .findFirst().orElseThrow();
        }

        private static void tick(RadialWheel wheel, int ticks) {
            for (int i = 0; i < ticks; i++) {
                wheel.tick();
            }
        }

        private static void assertSameLayout(List<RadialWheel.PetalArc> expected, List<RadialWheel.PetalArc> actual) {
            assertEquals(expected.size(), actual.size());
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).type(), actual.get(i).type());
                assertEquals(expected.get(i).ability(), actual.get(i).ability());
                assertEquals(expected.get(i).start(), actual.get(i).start(), EPSILON);
                assertEquals(expected.get(i).arc(), actual.get(i).arc(), EPSILON);
            }
        }

        /** Whether the open type's displayed petals span the cursor, any whole turn of it. */
        private static boolean openSpanHolds(List<RadialWheel.PetalArc> displayed, int type, double cursor) {
            List<RadialWheel.PetalArc> span = displayed.stream().filter(petal -> petal.type() == type).toList();
            double start = span.getFirst().start();
            double length = span.getLast().start() + span.getLast().arc() - start;
            return ((cursor - start) % TWO_PI + TWO_PI) % TWO_PI <= length + EPSILON;
        }

        private static void assertOpenSpanHoldsTheCursorAtEveryStep(RadialWheel wheel, double cursor) {
            for (int step = 0; step < RingEase.DURATION_TICKS; step++) {
                for (float partial : PARTIALS) {
                    assertTrue(openSpanHolds(wheel.displayedLayout(partial), wheel.selectedType(), cursor),
                            "step " + step + " partial " + partial);
                }
                wheel.tick();
            }
            assertTrue(openSpanHolds(wheel.displayedLayout(0.0f), wheel.selectedType(), cursor));
        }

        @Test
        void easeFromRestPassesBetweenAndLandsOnTheTargetThenHolds() {
            RadialWheel wheel = opened(ABILITIES);
            RadialWheel.PetalArc targetAbility = petalOf(wheel.layout(), OPEN_TYPE, 0);
            RadialWheel.PetalArc targetNeighbor = petalOf(wheel.layout(), OPEN_TYPE + 1, RadialWheel.NONE);

            assertSameLayout(wheel(ABILITIES).layout(), wheel.displayedLayout(0.0f));
            tick(wheel, MID_TICKS);
            List<RadialWheel.PetalArc> midway = wheel.displayedLayout(0.0f);
            double abilityArc = petalOf(midway, OPEN_TYPE, 0).arc();
            double neighborArc = petalOf(midway, OPEN_TYPE + 1, RadialWheel.NONE).arc();
            assertTrue(abilityArc > 0 && abilityArc < targetAbility.arc(), "ability arc " + abilityArc);
            assertTrue(neighborArc < TYPE_ARC && neighborArc > targetNeighbor.arc(), "neighbor arc " + neighborArc);
            tick(wheel, RingEase.DURATION_TICKS - MID_TICKS);
            assertSameLayout(wheel.layout(), wheel.displayedLayout(0.0f));
            tick(wheel, RingEase.DURATION_TICKS);
            assertSameLayout(wheel.layout(), wheel.displayedLayout(0.5f));
        }

        @Test
        void retargetMidEaseStartsFromTheDisplayedLayout() {
            RadialWheel wheel = opened(ABILITIES);
            tick(wheel, MID_TICKS);
            List<RadialWheel.PetalArc> before = wheel.displayedLayout(0.0f);

            moveTo(wheel, petalOf(wheel.layout(), OPEN_TYPE + 1, RadialWheel.NONE).center(), PETAL);

            assertEquals(OPEN_TYPE + 1, wheel.selectedType());
            assertSameLayout(before, wheel.displayedLayout(0.0f));
            assertTrue(wheel.isAnimating());
        }

        @Test
        void openSpanHoldsTheCursorThroughEveryEase() {
            RadialWheel wheel = Rotation.wheelWithAWideType();
            double opening = (Rotation.WIDE_TYPE + 0.5) * TYPE_ARC;
            moveTo(wheel, opening, PETAL);
            assertOpenSpanHoldsTheCursorAtEveryStep(wheel, opening);

            double counterclockwise = Rotation.WIDE_SPAN_START - Rotation.JUST_PAST;
            moveTo(wheel, counterclockwise, PETAL);
            assertOpenSpanHoldsTheCursorAtEveryStep(wheel, counterclockwise);

            moveTo(wheel, opening, PETAL);
            tick(wheel, RingEase.DURATION_TICKS);
            double clockwise = Rotation.WIDE_SPAN_END + Rotation.JUST_PAST;
            moveTo(wheel, clockwise, PETAL);
            assertEquals(Rotation.WIDE_TYPE + 1, wheel.selectedType());
            assertOpenSpanHoldsTheCursorAtEveryStep(wheel, clockwise);
        }

        @Test
        void animatingHoldsUntilTheEaseLands() {
            RadialWheel wheel = wheel(ABILITIES);
            assertFalse(wheel.isAnimating());

            moveTo(wheel, (OPEN_TYPE + 0.5) * TYPE_ARC, PETAL);
            tick(wheel, RingEase.DURATION_TICKS - 1);
            assertTrue(wheel.isAnimating());
            wheel.tick();
            assertFalse(wheel.isAnimating());
        }
    }

    @Nested
    class Scroll {

        @Test
        void scrollDownFromRestOpensTheFirstType() {
            RadialWheel wheel = wheel(ABILITIES);

            wheel.scroll(-1);

            assertEquals(0, wheel.selectedType());
        }

        @Test
        void scrollStepsAndWrapsTheOpenType() {
            RadialWheel wheel = wheel(ABILITIES);

            wheel.scroll(1);
            assertEquals(TYPES - 1, wheel.selectedType());
            wheel.scroll(-1);
            assertEquals(0, wheel.selectedType());
        }
    }
}
