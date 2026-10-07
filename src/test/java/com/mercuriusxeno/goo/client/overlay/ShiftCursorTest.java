package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.ShiftStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TeleportMode;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.client.ability.Afterimages;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shift cursor shows by its ability's indicator rule, for a blink and a
 * fungal shift alike, and restarts its
 * ripple every pulse period, so the silhouettes never stop while it shows
 * (decision ripple-outline-is-the-blink-cursor).
 */
class ShiftCursorTest {

    private static final long HELD_FROM = 1_003;

    private static ClientAbility ability(List<Step> behaviors, IndicatorShowing indicator) {
        return new ClientAbility(Identifier.parse("goo:ender_blink"), "blink", "", 0, List.of(), behaviors, 0,
                Delivery.of(DeliveryKind.SELF), AbilityBadge.SELF, List.of(), AbilityArea.NONE, indicator);
    }

    private static ClientAbility blink(IndicatorShowing indicator) {
        return ability(List.of(new TeleportStep(TeleportMode.THROWER_LOOK, Expr.literal(8))), indicator);
    }

    @Nested
    class ShowingRule {

        @Test
        void selectedShowsWithRightClickUp() {
            assertTrue(ShiftCursor.showsCursor(blink(IndicatorShowing.SELECTED), false));
        }

        @Test
        void heldHidesWithRightClickUp() {
            assertFalse(ShiftCursor.showsCursor(blink(IndicatorShowing.HELD), false));
        }

        @Test
        void heldShowsWhileRightClickIsHeld() {
            assertTrue(ShiftCursor.showsCursor(blink(IndicatorShowing.HELD), true));
        }

        @Test
        void anAbilityWithNoLookTeleportShowsNoCursor() {
            ClientAbility shuffle = ability(List.of(new TeleportStep(TeleportMode.RANDOM_OFFSET, Expr.literal(32))),
                    IndicatorShowing.SELECTED);
            assertFalse(ShiftCursor.showsCursor(shuffle, true));
            assertFalse(ShiftCursor.showsCursor(null, true));
        }

        @Test
        void aFungalShiftShowsWhileRightClickIsHeld() {
            ClientAbility shift = ability(List.of(new ShiftStep(Expr.literal(16))), IndicatorShowing.HELD);
            assertTrue(ShiftCursor.showsCursor(shift, true));
            assertFalse(ShiftCursor.showsCursor(shift, false));
        }
    }

    @Nested
    class Ripple {

        @Test
        void theNewestRippleRestartsEveryPulsePeriod() {
            long first = ShiftCursor.rippleStarts(HELD_FROM).getFirst();
            for (long now = HELD_FROM; now < HELD_FROM + 3L * ShiftCursor.PULSE_PERIOD_TICKS; now++) {
                long newest = ShiftCursor.rippleStarts(now).getFirst();
                assertEquals(0, newest % ShiftCursor.PULSE_PERIOD_TICKS, "a ripple started off the period");
                assertEquals(first + (now - first) / ShiftCursor.PULSE_PERIOD_TICKS * ShiftCursor.PULSE_PERIOD_TICKS,
                        newest, "the ripple did not restart on the period at " + now);
            }
        }

        @Test
        void aSilhouetteStandsEveryFrameWhileTheCursorShows() {
            for (float now = HELD_FROM; now < HELD_FROM + 4f * ShiftCursor.PULSE_PERIOD_TICKS; now += 0.25f) {
                int standing = 0;
                for (long start : ShiftCursor.rippleStarts((long) Math.floor(now))) {
                    standing += new Afterimages.Afterimage<>(new Object(), Vec3.ZERO, 0, start,
                            ShiftCursor.LIFE_TICKS).pulses(now).size();
                }
                assertTrue(standing > 0, "the ripple ended at " + now);
            }
        }

        @Test
        void theRipplesDrawnAreExactlyTheOnesStanding() {
            for (long now = HELD_FROM; now < HELD_FROM + ShiftCursor.PULSE_PERIOD_TICKS; now++) {
                List<Long> starts = ShiftCursor.rippleStarts(now);
                for (long start : starts) {
                    assertFalse(standing(start, now).isEmpty(), "a faded ripple is drawn at " + now);
                }
                long olderStill = starts.getLast() - ShiftCursor.PULSE_PERIOD_TICKS;
                assertTrue(standing(olderStill, now).isEmpty(), "a standing ripple was dropped at " + now);
            }
        }

        private static List<Afterimages.Pulse> standing(long start, long now) {
            return new Afterimages.Afterimage<>(new Object(), Vec3.ZERO, 0, start, ShiftCursor.LIFE_TICKS)
                    .pulses(now);
        }
    }
}
