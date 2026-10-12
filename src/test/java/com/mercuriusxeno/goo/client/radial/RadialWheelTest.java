package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the radial wheel's layout and its state transitions from cursor, scroll and click events: the types
 * at the rim around a wide neutral zone at rest, dropping into a band round a shrunken hub as a type opens, its
 * abilities fanned in the outer ring and turned only to rest the cursor in an ability's middle (decisions
 * abilities-replace-the-hovered-type, halves-arc-petals-at-rest, ring-rotates-to-keep-the-cursor-inside).
 */
class RadialWheelTest {

    /** The shipped goo types. */
    private static final int TYPES = 26;
    private static final int OPEN_TYPE = 3;
    private static final int ABILITIES = 4;
    /** More 30-degree petals than the whole ring holds. */
    private static final int MORE_THAN_FIT = 13;
    private static final double RADIUS = 100.0;
    /** Out on the outer ring, past the neutral zone at rest. */
    private static final double RIM = (RadialWheel.TYPE_INNER_FRACTION + 1.0) / 2 * RADIUS;
    /** Inside the neutral zone at rest, and on the type band once a type opens. */
    private static final double BAND = (RadialWheel.HUB_FRACTION + RadialWheel.TYPE_INNER_FRACTION) / 2 * RADIUS;
    /** Inside the hub at any size. */
    private static final double HUB = RadialWheel.HUB_FRACTION / 2 * RADIUS;
    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double TYPE_ARC = TWO_PI / TYPES;
    private static final double EPSILON = 1e-9;
    private static final double JUST_PAST = Math.toRadians(1.0);
    private static final double SWEEP_STEP = Math.toRadians(1.0);
    private static final int SWEEP_STEPS = 360;

    private static RadialWheel wheel(int abilities) {
        return new RadialWheel(TYPES, type -> abilities);
    }

    /** Moves the cursor to a clockwise angle from the top at a distance from the center. */
    private static void moveTo(RadialWheel wheel, double angle, double distance) {
        wheel.moveCursor(Math.sin(angle) * distance, -Math.cos(angle) * distance, RADIUS);
    }

    private static void settle(RadialWheel wheel) {
        for (int i = 0; i < RingEase.DURATION_TICKS; i++) {
            wheel.tick();
        }
    }

    /** The middle of a type's petal at rest. */
    private static double typeCenter(int type) {
        return (type + 0.5) * TYPE_ARC;
    }

    /** Opens a type with the cursor at an angle on its petal tip and lets the fan settle. */
    private static RadialWheel openedAt(int abilities, double angle) {
        RadialWheel wheel = wheel(abilities);
        moveTo(wheel, angle, RIM);
        settle(wheel);
        return wheel;
    }

    private static RadialWheel opened(int abilities) {
        return openedAt(abilities, typeCenter(OPEN_TYPE));
    }

    private static List<RadialWheel.PetalArc> typePetals(RadialWheel wheel) {
        return wheel.layout().stream().filter(petal -> !petal.isAbility()).toList();
    }

    private static List<RadialWheel.PetalArc> abilityPetals(RadialWheel wheel) {
        return wheel.layout().stream().filter(RadialWheel.PetalArc::isAbility).toList();
    }

    private static RadialWheel.PetalArc abilityPetal(RadialWheel wheel, int ability) {
        return abilityPetals(wheel).stream().filter(petal -> petal.ability() == ability).findFirst().orElseThrow();
    }

    private static double middleOf(List<RadialWheel.PetalArc> petals) {
        return (petals.getFirst().start() + petals.getLast().start() + petals.getLast().arc()) / 2;
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
        void atRestEveryTypeIsAPetalTipClockwiseFromTwelveRoundANeutralZoneHalfTheWheel() {
            RadialWheel wheel = wheel(ABILITIES);
            List<RadialWheel.PetalArc> petals = wheel.layout();

            assertEquals(RadialWheel.HUB_REST_FRACTION, wheel.displayedHubRadius(0.0f), EPSILON);
            assertEquals(IntStream.range(0, TYPES).boxed().toList(), petals.stream().map(RadialWheel.PetalArc::type)
                    .toList());
            assertAll(petals.stream().map(petal -> (Executable) () -> {
                assertFalse(petal.isAbility());
                assertEquals(petal.type() * TYPE_ARC, petal.start(), EPSILON);
                assertEquals(TYPE_ARC, petal.arc(), EPSILON);
                assertEquals(RadialWheel.HUB_REST_FRACTION, petal.shape().inner(), EPSILON, "out past the neutral zone");
                assertEquals(1.0, petal.length(), EPSILON);
            }));
        }

        /** decision halves-arc-petals-at-rest */
        @Test
        void openingATypeDropsTheTypesIntoABandRoundTheShrunkenHubKeepingTheirAngles() {
            RadialWheel wheel = opened(ABILITIES);

            assertEquals(RadialWheel.HUB_FRACTION, wheel.displayedHubRadius(0.0f), EPSILON);
            assertEquals(TYPES, typePetals(wheel).size());
            assertAll(typePetals(wheel).stream().map(petal -> (Executable) () -> {
                assertEquals(petal.type() * TYPE_ARC, petal.start(), EPSILON, "the types never turn");
                assertEquals(RadialWheel.HUB_FRACTION, petal.shape().inner(), EPSILON);
                assertEquals(RadialWheel.TYPE_BAND_FRACTION, petal.length(), EPSILON);
            }));
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void theOpenTypesAbilitiesFanInTheOuterRingAtThirtyDegreesEach() {
            RadialWheel wheel = opened(ABILITIES);
            List<RadialWheel.PetalArc> abilities = abilityPetals(wheel);

            assertEquals(ABILITIES, abilities.size());
            assertAll(abilities.stream().map(petal -> (Executable) () -> {
                assertEquals(OPEN_TYPE, petal.type());
                assertEquals(RadialWheel.ABILITY_ARC, petal.arc(), EPSILON);
                assertEquals(RadialWheel.HUB_FRACTION, petal.shape().inner(), EPSILON, "reaching in behind the types");
            }));
            for (int i = 1; i < abilities.size(); i++) {
                assertEquals(abilities.get(i - 1).start() + abilities.get(i - 1).arc(), abilities.get(i).start(),
                        EPSILON, "fanned edge to edge");
            }
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void abilitiesDrawFirstReachingInBehindTheTypePetals() {
            List<RadialWheel.PetalArc> drawn = opened(ABILITIES).displayedLayout(0.0f);
            int lastAbility = IntStream.range(0, drawn.size()).filter(i -> drawn.get(i).isAbility()).max().orElseThrow();
            int firstType = IntStream.range(0, drawn.size()).filter(i -> !drawn.get(i).isAbility()).min().orElseThrow();

            assertTrue(lastAbility < firstType, "a type petal drew under an ability");
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void moreAbilitiesThanTheRingHoldsShareItEvenly() {
            RadialWheel wheel = opened(MORE_THAN_FIT);

            assertAll(abilityPetals(wheel).stream().map(petal -> (Executable)
                    () -> assertEquals(TWO_PI / MORE_THAN_FIT, petal.arc(), EPSILON)));
        }

        /** decision ring-rotates-to-keep-the-cursor-inside */
        @Test
        void theFanCentersOnItsTypesPetal() {
            RadialWheel wheel = openedAt(ABILITIES, typeCenter(OPEN_TYPE) + TYPE_ARC / 3);

            assertEquals(OPEN_TYPE, wheel.selectedType());
            assertEquals(0.0, Math.IEEEremainder(middleOf(abilityPetals(wheel)) - typeCenter(OPEN_TYPE), TWO_PI),
                    EPSILON);
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
            RadialWheel wheel = new RadialWheel(0, type -> 0);

            moveTo(wheel, 0.0, RIM);
            wheel.scroll(-1);
            wheel.tick();

            assertEquals(RadialWheel.Outcome.CANCEL, wheel.click());
        }
    }

    @Nested
    class Transitions {

        /** decision halves-arc-petals-at-rest */
        @Test
        void theWideNeutralZoneOpensNothing() {
            RadialWheel wheel = wheel(ABILITIES);

            moveTo(wheel, typeCenter(OPEN_TYPE), BAND);

            assertFalse(wheel.isOpen());
        }

        /** decision halves-arc-petals-at-rest: the spot that opens a type at rest lands on its dropped petal, so nothing flutters */
        @Test
        void theSpotThatOpensATypeKeepsItOpenOnItsDroppedPetal() {
            RadialWheel wheel = wheel(ABILITIES);
            double justOutside = (RadialWheel.TYPE_INNER_FRACTION + 0.01) * RADIUS;

            moveTo(wheel, typeCenter(OPEN_TYPE), justOutside);
            assertEquals(OPEN_TYPE, wheel.selectedType(), "just past the neutral zone opens the type");
            moveTo(wheel, typeCenter(OPEN_TYPE), justOutside);

            assertEquals(OPEN_TYPE, wheel.selectedType(), "the same spot keeps it open");
            assertEquals(RadialWheel.NONE, wheel.hoveredAbility(), "on the type petal no ability is hovered");
        }

        @Test
        void cursorOnATypesPetalTipOpensIt() {
            RadialWheel wheel = opened(ABILITIES);

            assertEquals(OPEN_TYPE, wheel.selectedType());
            assertTrue(wheel.isOpen());
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void mousingThroughTheDroppedTypePetalsOpensEachTypeInTurn() {
            RadialWheel wheel = opened(ABILITIES);

            for (int next = OPEN_TYPE + 1; next <= OPEN_TYPE + 3; next++) {
                int type = next;
                moveTo(wheel, typeCenter(type), BAND);

                assertEquals(type, wheel.selectedType());
                assertEquals(RadialWheel.NONE, wheel.hoveredAbility(), "on the type petal no ability is hovered");
                assertTrue(abilityPetals(wheel).stream().allMatch(petal -> petal.type() == type),
                        "the fan shows the type under the cursor");
            }
        }

        /** decision petal-moves-animate: a closed fan folds away on its own clock, whatever opens after it */
        @Test
        void aFanFoldsAwayWithinTheEaseEvenWhileTheCursorKeepsOpeningTypes() {
            RadialWheel wheel = opened(ABILITIES);

            for (int tick = 1; tick <= RingEase.DURATION_TICKS; tick++) {
                moveTo(wheel, typeCenter(OPEN_TYPE + tick), BAND);
                wheel.tick();
            }

            assertTrue(wheel.displayedLayout(0.0f).stream()
                    .noneMatch(petal -> petal.isAbility() && petal.type() == OPEN_TYPE), "the first fan has folded away");
        }

        /** decision halves-arc-petals-at-rest */
        @Test
        void cursorInTheShrunkenHubReturnsToRest() {
            RadialWheel wheel = opened(ABILITIES);

            moveTo(wheel, typeCenter(OPEN_TYPE + 2), HUB);

            assertFalse(wheel.isOpen());
            assertTrue(abilityPetals(wheel).isEmpty());
            assertEquals(RadialWheel.Outcome.CANCEL, wheel.click());
        }
        @Test
        void cursorOnAnAbilityPetalHoversIt() {
            RadialWheel wheel = opened(ABILITIES);
            int other = (wheel.hoveredAbility() + 1) % ABILITIES;

            moveTo(wheel, abilityPetal(wheel, other).center(), RIM);

            assertEquals(other, wheel.hoveredAbility());
        }

        /** decision ring-rotates-to-keep-the-cursor-inside */
        @Test
        void leavingTheFanEitherWayWindsOnToTheNeighboringTypeWithTheCursorInAnAbility() {
            assertAll(IntStream.of(1, -1).mapToObj(step -> (Executable) () -> {
                RadialWheel wheel = opened(ABILITIES);
                List<RadialWheel.PetalArc> fan = abilityPetals(wheel);
                double cursor = step > 0 ? fan.getLast().start() + fan.getLast().arc() + JUST_PAST
                        : fan.getFirst().start() - JUST_PAST;

                moveTo(wheel, cursor, RIM);

                assertEquals(Math.floorMod(OPEN_TYPE + step, TYPES), wheel.selectedType(), "winding " + step);
                assertTrue(wheel.hoveredAbility() != RadialWheel.NONE, "the cursor lands in an ability");
            }));
        }

        @Test
        void clickOnAnAbilitySelectsItOnTheGlove() {
            RadialWheel wheel = opened(ABILITIES);
            moveTo(wheel, abilityPetal(wheel, 2).center(), RIM);

            RadialWheel.Outcome outcome = wheel.click();

            assertTrue(outcome.selects());
            assertEquals(new RadialWheel.Outcome(OPEN_TYPE, 2), outcome);
        }

        @Test
        void aTypeWithNoAbilitiesOpensToAnEmptyOuterRingAndCancels() {
            RadialWheel wheel = opened(0);

            assertEquals(OPEN_TYPE, wheel.selectedType());
            assertTrue(abilityPetals(wheel).isEmpty());
            assertEquals(RadialWheel.Outcome.CANCEL, wheel.click());
        }
    }

    /**
     * The open type's fan sits centered on its petal and never turns: the
     * cursor hovers whichever ability lies under it, sweeping either way
     * reaches every ability in turn, and past either end the cursor winds on
     * to the neighboring type (decision ring-rotates-to-keep-the-cursor-inside).
     */
    @Nested
    class Fan {

        /** Sweeps the cursor a degree at a time one way across the open type's fan, answering each ability it hovered. */
        private static List<Integer> sweepAcross(RadialWheel wheel, double direction) {
            List<Integer> hovered = new ArrayList<>(List.of(wheel.hoveredAbility()));
            double cursor = abilityPetal(wheel, wheel.hoveredAbility()).center();
            List<Double> fan = abilityPetals(wheel).stream().map(RadialWheel.PetalArc::start).toList();
            for (int step = 0; step < SWEEP_STEPS; step++) {
                double next = cursor + direction * SWEEP_STEP;
                // the sweep stops at the fan's edge, past which the cursor winds on to the next type
                if (abilityPetals(wheel).stream().noneMatch(petal ->
                        Math.abs(Math.IEEEremainder(next - petal.center(), TWO_PI)) * 2 < petal.arc())) {
                    break;
                }
                moveTo(wheel, next, RIM);
                assertEquals(fan, abilityPetals(wheel).stream().map(RadialWheel.PetalArc::start).toList(),
                        "the fan never moves");
                cursor = next;
                if (wheel.hoveredAbility() != hovered.getLast()) {
                    hovered.add(wheel.hoveredAbility());
                }
            }
            return hovered;
        }

        @Test
        void openingATypeHoversTheAbilityUnderTheCursorTheFanCenteredOnItsPetal() {
            double cursor = typeCenter(OPEN_TYPE) + TYPE_ARC / 3;
            RadialWheel wheel = openedAt(ABILITIES, cursor);
            RadialWheel.PetalArc hovered = abilityPetal(wheel, wheel.hoveredAbility());

            assertEquals(0.0, Math.IEEEremainder(middleOf(abilityPetals(wheel)) - typeCenter(OPEN_TYPE), TWO_PI),
                    EPSILON);
            assertTrue(Math.abs(Math.IEEEremainder(cursor - hovered.center(), TWO_PI)) * 2 < hovered.arc(),
                    "the hovered ability lies under the cursor");
        }

        @Test
        void sweepingEitherWayReachesEveryAbilityThatWayInTurnWithTheFanStill() {
            assertAll(DoubleStream.of(1.0, -1.0).mapToObj(direction -> (Executable) () -> {
                RadialWheel wheel = opened(ABILITIES);
                int from = wheel.hoveredAbility();
                int last = direction > 0 ? ABILITIES - 1 : 0;

                List<Integer> hovered = sweepAcross(wheel, direction);

                assertEquals(IntStream.iterate(from, ability -> ability + (int) direction)
                        .limit(Math.abs(last - from) + 1).boxed().toList(), hovered, "sweeping " + direction);
            }));
        }
    }
    /**
     * Opening a type plays like a fan of cards, the types dropping into the
     * band as the abilities fan out from stacked on the type's center;
     * closing plays it in reverse (decisions petal-moves-animate, mid-animation-input-does-nothing).
     */
    @Nested
    class Ease {

        private static final int MID_TICKS = RingEase.DURATION_TICKS / 2;

        @Test
        void openingDropsTheTypesAndFansTheCardsPartWayAtMidEase() {
            RadialWheel wheel = wheel(ABILITIES);
            moveTo(wheel, typeCenter(OPEN_TYPE), RIM);

            for (int i = 0; i < MID_TICKS; i++) {
                wheel.tick();
            }
            List<RadialWheel.PetalArc> midway = wheel.displayedLayout(0.0f);
            double hub = wheel.displayedHubRadius(0.0f);
            List<RadialWheel.PetalArc> cards = midway.stream().filter(RadialWheel.PetalArc::isAbility).toList();
            double spread = cards.getLast().start() - cards.getFirst().start();

            assertTrue(hub > RadialWheel.HUB_FRACTION && hub < RadialWheel.HUB_REST_FRACTION, "hub " + hub);
            assertTrue(spread > 0 && spread < (ABILITIES - 1) * RadialWheel.ABILITY_ARC, "spread " + spread);
        }

        /** Asserts every ability petal on display starts where the type petals' bases stand. */
        private void assertTailsOnTheTypeBases(RadialWheel wheel) {
            List<RadialWheel.PetalArc> drawn = wheel.displayedLayout(0.0f);
            double base = drawn.stream().filter(petal -> !petal.isAbility()).findFirst().orElseThrow().shape().inner();
            assertTrue(drawn.stream().anyMatch(RadialWheel.PetalArc::isAbility), "a fan shows mid-ease");
            assertAll(drawn.stream().filter(RadialWheel.PetalArc::isAbility).map(petal -> (Executable) ()
                    -> assertEquals(base, petal.shape().inner(), EPSILON, petal + " tail off the type bases")));
            assertEquals(base, wheel.displayedHubRadius(0.0f), EPSILON, "the type bases sit on the neutral zone's edge");
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void theAbilityTailsRideWithTheTypeBasesOpeningAndClosing() {
            RadialWheel wheel = wheel(ABILITIES);
            moveTo(wheel, typeCenter(OPEN_TYPE), RIM);
            for (int i = 0; i < MID_TICKS; i++) {
                wheel.tick();
            }
            assertTailsOnTheTypeBases(wheel);

            settle(wheel);
            moveTo(wheel, 0.0, HUB);
            for (int i = 0; i < MID_TICKS; i++) {
                wheel.tick();
            }
            assertTailsOnTheTypeBases(wheel);
        }

        @Test
        void closingPlaysTheFanInReverse() {
            RadialWheel wheel = opened(ABILITIES);

            moveTo(wheel, 0.0, HUB);
            for (int i = 0; i < MID_TICKS; i++) {
                wheel.tick();
            }
            double hub = wheel.displayedHubRadius(0.0f);

            assertTrue(hub > RadialWheel.HUB_FRACTION && hub < RadialWheel.HUB_REST_FRACTION, "hub " + hub);
            assertTrue(wheel.displayedLayout(0.0f).stream().anyMatch(RadialWheel.PetalArc::isAbility),
                    "the cards fold back in");
        }

        /** decision abilities-replace-the-hovered-type */
        @Test
        void hoveredAbilityGrowsToTheScreensEdgeAndEasesBackWhenLeft() {
            RadialWheel wheel = opened(ABILITIES);
            int first = wheel.hoveredAbility();
            int other = (first + 1) % ABILITIES;
            settle(wheel);
            assertEquals(RadialWheel.HOVER_REACH, cardOf(wheel, first).length(), EPSILON);

            moveTo(wheel, abilityPetal(wheel, other).center(), RIM);
            wheel.tick();
            double midway = cardOf(wheel, first).length();
            for (int i = 0; i < RadialWheel.LIFT_TICKS; i++) {
                wheel.tick();
            }

            assertTrue(midway > 1.0 && midway < RadialWheel.HOVER_REACH, "easing back " + midway);
            assertEquals(1.0, cardOf(wheel, first).length(), EPSILON);
            assertEquals(RadialWheel.HOVER_REACH, cardOf(wheel, other).length(), EPSILON);
        }

        private RadialWheel.PetalArc cardOf(RadialWheel wheel, int ability) {
            return wheel.displayedLayout(0.0f).stream().filter(petal -> petal.ability() == ability)
                    .findFirst().orElseThrow();
        }

        @Test
        void animatingHoldsUntilTheEaseLands() {
            RadialWheel wheel = wheel(ABILITIES);
            assertFalse(wheel.isAnimating());

            moveTo(wheel, typeCenter(OPEN_TYPE), RIM);
            for (int i = 0; i < RingEase.DURATION_TICKS - 1; i++) {
                wheel.tick();
            }
            assertTrue(wheel.isAnimating());
            wheel.tick();
            assertFalse(wheel.isAnimating());
        }
    }

    /**
     * The scroll wheel steps through every ability of every type in list
     * order, the fan at rest, so the radial works on a controller (decision abilities-replace-the-hovered-type).
     */
    @Nested
    class Scroll {

        private static final int EMPTY_TYPE = 1;

        /** Types 0 and 2 onward open to two abilities each; type 1 opens to none. */
        private static RadialWheel wheelWithAnEmptyType() {
            return new RadialWheel(TYPES, type -> type == EMPTY_TYPE ? 0 : 2);
        }

        private static void assertHovering(RadialWheel wheel, int type, int ability) {
            assertEquals(type, wheel.selectedType(), "type");
            assertEquals(ability, wheel.hoveredAbility(), "ability");
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
    }
}
