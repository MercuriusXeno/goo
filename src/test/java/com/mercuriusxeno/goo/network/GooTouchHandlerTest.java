package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A mob ability flying a line touches an entity within reach and throws
 * beyond it, and a touch drains its cost before it strikes
 * (decision mob-ability-touches-at-reach).
 */
class GooTouchHandlerTest {

    private static final double REACH = 3.0;
    private static final double WITHIN = REACH * REACH;
    private static final double BEYOND = (REACH + 0.1) * (REACH + 0.1);
    private static final int COST = 1000;

    @Nested
    class Rule {

        @ParameterizedTest
        @EnumSource(value = DeliveryKind.class, names = {"ARC", "BEAM"})
        void mobAbilityFlyingALineTouchesAnEntityAtReach(DeliveryKind kind) {
            assertTrue(GooTouchHandler.touches(Delivery.of(kind), AbilityBadge.MOB, true, WITHIN, REACH));
        }

        @Test
        void entityBeyondReachThrows() {
            assertFalse(GooTouchHandler.touches(Delivery.ARC, AbilityBadge.MOB, true, BEYOND, REACH));
        }

        @Test
        void blockTargetThrows() {
            assertFalse(GooTouchHandler.touches(Delivery.ARC, AbilityBadge.MOB, false, WITHIN, REACH));
        }

        @ParameterizedTest
        @EnumSource(value = AbilityBadge.class, names = "MOB", mode = EnumSource.Mode.EXCLUDE)
        void otherBadgesThrow(AbilityBadge badge) {
            assertFalse(GooTouchHandler.touches(Delivery.ARC, badge, true, WITHIN, REACH));
        }

        @ParameterizedTest
        @EnumSource(value = DeliveryKind.class, names = {"STREAM", "SELF"})
        void deliveriesFlyingNoLineNeverTouch(DeliveryKind kind) {
            assertFalse(GooTouchHandler.touchesAtReach(Delivery.of(kind), AbilityBadge.MOB));
        }
    }

    @Nested
    class Landing {

        @Test
        void affordableTouchDrainsTheCostThenStrikes() {
            GooTouchHandler.TouchLanding landing = mock(GooTouchHandler.TouchLanding.class);
            when(landing.canAfford(COST)).thenReturn(true);

            assertTrue(GooTouchHandler.landIfAffordable(COST, landing));

            var order = inOrder(landing);
            order.verify(landing).deplete(COST);
            order.verify(landing).strike();
        }

        @Test
        void unaffordableTouchDrainsNothingAndStrikesNothing() {
            GooTouchHandler.TouchLanding landing = mock(GooTouchHandler.TouchLanding.class);

            assertFalse(GooTouchHandler.landIfAffordable(COST, landing));

            verify(landing, never()).deplete(anyInt());
            verify(landing, never()).strike();
        }
    }
}
