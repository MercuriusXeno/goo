package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A punch lands only on a target within reach the puncher can pay for, and
 * one beyond reach drains nothing (decision punch-strikes-at-reach).
 */
class GooPunchHandlerTest {

    private static final double REACH = 3.0;
    private static final int COST = 1000;
    private static final double PLAYER_REACH = 3.0;

    private static GooPunchHandler.PunchLanding affordingLanding() {
        GooPunchHandler.PunchLanding landing = mock(GooPunchHandler.PunchLanding.class);
        when(landing.canAfford(COST)).thenReturn(true);
        return landing;
    }

    @Nested
    class Reach {

        @Test
        void targetBeyondReachRefusesAndDrainsNothing() {
            GooPunchHandler.PunchLanding landing = affordingLanding();
            double beyond = REACH + 1;

            assertFalse(GooPunchHandler.landIfInReach(beyond * beyond, REACH, COST, landing));

            verify(landing, never()).deplete(anyInt());
            verify(landing, never()).strike();
        }

        @Test
        void targetAtReachDrainsTheCostThenStrikes() {
            GooPunchHandler.PunchLanding landing = affordingLanding();

            assertTrue(GooPunchHandler.landIfInReach(REACH * REACH, REACH, COST, landing));

            var order = inOrder(landing);
            order.verify(landing).deplete(COST);
            order.verify(landing).strike();
        }

        @Test
        void unaffordablePunchDrainsNothing() {
            GooPunchHandler.PunchLanding landing = mock(GooPunchHandler.PunchLanding.class);

            assertFalse(GooPunchHandler.landIfInReach(1, REACH, COST, landing));

            verify(landing, never()).deplete(anyInt());
        }
    }

    @Nested
    class ReachOfDelivery {

        @Test
        void deliveryNamingNoRangeTakesThePlayersReach() {
            assertEquals(PLAYER_REACH, GooPunchHandler.reach(Delivery.of(DeliveryKind.PUNCH), PLAYER_REACH));
        }

        @Test
        void deliveryRangeOverridesThePlayersReach() {
            Delivery longArm = new Delivery(DeliveryKind.PUNCH, Delivery.DEFAULT_BLOCKS_PER_TICK, 5,
                    Delivery.DEFAULT_CONE_DEGREES, Delivery.DEFAULT_TICKS_PER_CHARGE, true);
            assertEquals(5, GooPunchHandler.reach(longArm, PLAYER_REACH));
        }
    }
}
