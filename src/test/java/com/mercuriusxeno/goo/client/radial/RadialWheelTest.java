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
            assertEquals(RadialWheel.NONE, wheel.hoveredAbility());
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
            assertEquals(RadialWheel.Outcome.CANCEL, opened(ABILITIES).click());
        }

        @Test
        void typeWithNoAbilitiesKeepsItsOwnPetalWhenOpen() {
            RadialWheel wheel = opened(0);

            assertEquals(OPEN_TYPE, wheel.selectedType());
            assertEquals(TYPES, typePetals(wheel).size());
            assertEquals(RadialWheel.Outcome.CANCEL, wheel.click());
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
