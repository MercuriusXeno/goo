package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the one radial wheel's layout and its state transitions from cursor, scroll and click events (decision type-recedes-and-abilities-fan-out). */
class RadialWheelTest {

    private static final int TYPES = 16;
    private static final int FANNED_TYPE = 3;
    private static final int ABILITIES = 3;
    private static final double RADIUS = 100.0;
    private static final double INNER_RING = (RadialWheel.HUB_FRACTION + RadialWheel.RING_FRACTION) / 2 * RADIUS;
    private static final double OUTER_RING = (RadialWheel.RING_FRACTION + 1.0) / 2 * RADIUS;
    private static final double HUB = RadialWheel.HUB_FRACTION / 2 * RADIUS;
    private static final double EPSILON = 1e-9;

    private static RadialWheel wheel() {
        return new RadialWheel(TYPES, type -> ABILITIES);
    }

    /** Moves the cursor to a clockwise angle from the top at a distance from the center. */
    private static void moveTo(RadialWheel wheel, double angle, double distance) {
        wheel.moveCursor(Math.sin(angle) * distance, -Math.cos(angle) * distance, RADIUS);
    }

    private static RadialWheel fanned() {
        RadialWheel wheel = wheel();
        moveTo(wheel, wheel.typeCenter(FANNED_TYPE), INNER_RING);
        return wheel;
    }

    private static double abilityCenter(RadialWheel wheel, int ability) {
        return wheel.fanStart(FANNED_TYPE) + (ability + 0.5) * wheel.fanArc(FANNED_TYPE);
    }

    @Nested
    class Layout {

        @Test
        void radiusIsSixtyPercentOfTheSmallerDimensionHalved() {
            assertEquals(1080 * 0.6 / 2, RadialWheel.outerRadius(1920, 1080), EPSILON);
            assertEquals(800 * 0.6 / 2, RadialWheel.outerRadius(800, 1200), EPSILON);
        }

        @Test
        void cursorInTheInnerRingAtATypesAngleSelectsIt() {
            RadialWheel wheel = wheel();

            moveTo(wheel, wheel.typeCenter(FANNED_TYPE), INNER_RING);

            assertEquals(FANNED_TYPE, wheel.selectedType());
            assertTrue(wheel.isFanned());
        }

        @Test
        void fanWedgesSpanAParentArcEachCenteredOnTheParentAngle() {
            RadialWheel wheel = wheel();

            assertEquals(wheel.typeArc(), wheel.fanArc(FANNED_TYPE), EPSILON);
            assertEquals(wheel.typeCenter(FANNED_TYPE),
                    wheel.fanStart(FANNED_TYPE) + ABILITIES * wheel.fanArc(FANNED_TYPE) / 2, EPSILON);
        }

        @Test
        void cursorInTheOuterRingWithNoTypeSelectedSelectsNothing() {
            RadialWheel wheel = wheel();

            moveTo(wheel, wheel.typeCenter(FANNED_TYPE), OUTER_RING);

            assertFalse(wheel.isFanned());
        }
    }

    @Nested
    class Transitions {

        @Test
        void cursorOnAnAbilityWedgeHoversIt() {
            RadialWheel wheel = fanned();

            moveTo(wheel, abilityCenter(wheel, 2), OUTER_RING);

            assertEquals(2, wheel.hoveredAbility());
        }

        @Test
        void cursorPastEitherEndOfTheFanHoversNothingAndKeepsTheFan() {
            RadialWheel wheel = fanned();

            moveTo(wheel, abilityCenter(wheel, ABILITIES), OUTER_RING);
            assertEquals(RadialWheel.NONE, wheel.hoveredAbility());
            moveTo(wheel, abilityCenter(wheel, -1), OUTER_RING);
            assertEquals(RadialWheel.NONE, wheel.hoveredAbility());

            assertEquals(FANNED_TYPE, wheel.selectedType());
        }

        @Test
        void clickOnAnAbilitySelectsItOnTheGlove() {
            RadialWheel wheel = fanned();
            moveTo(wheel, abilityCenter(wheel, 1), OUTER_RING);

            RadialWheel.Outcome outcome = wheel.click();

            assertTrue(outcome.selects());
            assertEquals(new RadialWheel.Outcome(FANNED_TYPE, 1), outcome);
        }

        @Test
        void clickOffAnAbilityInTheFanCancels() {
            RadialWheel wheel = fanned();
            moveTo(wheel, abilityCenter(wheel, ABILITIES), OUTER_RING);

            assertEquals(RadialWheel.Outcome.CANCEL, wheel.click());
        }

        @Test
        void clickInTheTypeRingCancels() {
            RadialWheel wheel = fanned();

            assertEquals(RadialWheel.Outcome.CANCEL, wheel.click());
        }

        @Test
        void cursorInTheHubCollapsesTheFanToTypes() {
            RadialWheel wheel = fanned();

            moveTo(wheel, wheel.typeCenter(FANNED_TYPE), HUB);

            assertFalse(wheel.isFanned());
            assertEquals(RadialWheel.Outcome.CANCEL, wheel.click());
        }

        @Test
        void rightClickCancelsWithAnAbilityHovered() {
            RadialWheel wheel = fanned();
            moveTo(wheel, abilityCenter(wheel, 1), OUTER_RING);

            assertEquals(RadialWheel.Outcome.CANCEL, wheel.rightClick());
        }

        @Test
        void hoveringAnotherTypeRefansAndClearsTheHover() {
            RadialWheel wheel = fanned();
            moveTo(wheel, abilityCenter(wheel, 1), OUTER_RING);

            moveTo(wheel, wheel.typeCenter(FANNED_TYPE + 1), INNER_RING);

            assertEquals(FANNED_TYPE + 1, wheel.selectedType());
            assertEquals(RadialWheel.NONE, wheel.hoveredAbility());
        }
    }

    @Nested
    class Scroll {

        @Test
        void scrollDownFromTypesSelectsTheFirstType() {
            RadialWheel wheel = wheel();

            wheel.scroll(-1);

            assertEquals(0, wheel.selectedType());
        }

        @Test
        void scrollStepsAndWrapsTheSelectedType() {
            RadialWheel wheel = wheel();

            wheel.scroll(1);
            assertEquals(TYPES - 1, wheel.selectedType());
            wheel.scroll(-1);
            assertEquals(0, wheel.selectedType());
        }
    }
}
