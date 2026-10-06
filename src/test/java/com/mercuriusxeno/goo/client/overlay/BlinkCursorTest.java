package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.ability.program.Expr;
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
 * The blink cursor shows by its ability's indicator rule and restarts its
 * ripple every pulse period, so the silhouettes never stop while it shows
 * (decision ripple-outline-is-the-blink-cursor).
 */
class BlinkCursorTest {

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
            assertTrue(BlinkCursor.showsCursor(blink(IndicatorShowing.SELECTED), false));
        }

        @Test
        void heldHidesWithRightClickUp() {
            assertFalse(BlinkCursor.showsCursor(blink(IndicatorShowing.HELD), false));
        }

        @Test
        void heldShowsWhileRightClickIsHeld() {
            assertTrue(BlinkCursor.showsCursor(blink(IndicatorShowing.HELD), true));
        }

        @Test
        void anAbilityWithNoLookTeleportShowsNoCursor() {
            ClientAbility shuffle = ability(List.of(new TeleportStep(TeleportMode.RANDOM_OFFSET, Expr.literal(32))),
                    IndicatorShowing.SELECTED);
            assertFalse(BlinkCursor.showsCursor(shuffle, true));
            assertFalse(BlinkCursor.showsCursor(null, true));
        }
    }

    @Nested
    class Ripple {

        @Test
        void theNewestRippleRestartsEveryPulsePeriod() {
            long first = BlinkCursor.rippleStarts(HELD_FROM).getFirst();
            for (long now = HELD_FROM; now < HELD_FROM + 3L * BlinkCursor.PULSE_PERIOD_TICKS; now++) {
                long newest = BlinkCursor.rippleStarts(now).getFirst();
                assertEquals(0, newest % BlinkCursor.PULSE_PERIOD_TICKS, "a ripple started off the period");
                assertEquals(first + (now - first) / BlinkCursor.PULSE_PERIOD_TICKS * BlinkCursor.PULSE_PERIOD_TICKS,
                        newest, "the ripple did not restart on the period at " + now);
            }
        }

        @Test
        void aSilhouetteStandsEveryFrameWhileTheCursorShows() {
            for (float now = HELD_FROM; now < HELD_FROM + 4f * BlinkCursor.PULSE_PERIOD_TICKS; now += 0.25f) {
                int standing = 0;
                for (long start : BlinkCursor.rippleStarts((long) Math.floor(now))) {
                    standing += new Afterimages.Afterimage<>(new Object(), Vec3.ZERO, 0, start,
                            BlinkCursor.LIFE_TICKS).pulses(now).size();
                }
                assertTrue(standing > 0, "the ripple ended at " + now);
            }
        }

        @Test
        void theRipplesDrawnAreExactlyTheOnesStanding() {
            for (long now = HELD_FROM; now < HELD_FROM + BlinkCursor.PULSE_PERIOD_TICKS; now++) {
                List<Long> starts = BlinkCursor.rippleStarts(now);
                for (long start : starts) {
                    assertFalse(standing(start, now).isEmpty(), "a faded ripple is drawn at " + now);
                }
                long olderStill = starts.getLast() - BlinkCursor.PULSE_PERIOD_TICKS;
                assertTrue(standing(olderStill, now).isEmpty(), "a standing ripple was dropped at " + now);
            }
        }

        private static List<Afterimages.Pulse> standing(long start, long now) {
            return new Afterimages.Afterimage<>(new Object(), Vec3.ZERO, 0, start, BlinkCursor.LIFE_TICKS)
                    .pulses(now);
        }
    }
}
