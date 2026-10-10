package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The held route: a stream and a self delivery wearing the channeled badge
 * run on every held tick, a self delivery wearing any other badge and an arc
 * do not, and only the channeled self runs on the player
 * (decisions stream-delivery-held-cone,
 * flatten-disc-cursor-breaks-above-the-plane).
 */
class HeldRouteTest {

    private static final Delivery SELF = Delivery.of(DeliveryKind.SELF);
    private static final Delivery STREAM = Delivery.of(DeliveryKind.STREAM);

    @Test
    void streamRunsWhileHeldButIsNoChannel() {
        assertTrue(HeldRoute.runsWhileHeld(STREAM, AbilityBadge.CHANNELED));
        assertFalse(HeldRoute.channelsOnSelf(STREAM, AbilityBadge.CHANNELED));
    }

    @Test
    void channeledSelfRunsWhileHeldOnThePlayer() {
        assertTrue(HeldRoute.runsWhileHeld(SELF, AbilityBadge.CHANNELED));
        assertTrue(HeldRoute.channelsOnSelf(SELF, AbilityBadge.CHANNELED));
    }

    @Test
    void selfWearingTheSelfBadgeRunsOnCommand() {
        assertFalse(HeldRoute.runsWhileHeld(SELF, AbilityBadge.SELF));
    }

    @Test
    void channeledArcThrowsOnRelease() {
        assertFalse(HeldRoute.runsWhileHeld(Delivery.ARC, AbilityBadge.CHANNELED));
    }

    @Test
    void noSelectionRunsNothing() {
        assertFalse(HeldRoute.runsWhileHeld(null, null));
    }

    // nova-ring-grows-with-the-hold
    @Test
    void chargedChannelFiresOnReleaseRatherThanWhileHeld() {
        Delivery charged = new Delivery(DeliveryKind.SELF, Delivery.DEFAULT_BLOCKS_PER_TICK, 0,
                Delivery.DEFAULT_CONE_DEGREES, Delivery.DEFAULT_TICKS_PER_CHARGE, true, java.util.Optional.empty(),
                Delivery.DEFAULT_TRANSFORM_AT, java.util.Optional.empty(), 60);
        assertFalse(HeldRoute.runsWhileHeld(charged, AbilityBadge.CHANNELED));
    }
}
