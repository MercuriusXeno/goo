package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The held route: a stream and a self delivery wearing the channeled or held
 * badge run on every held tick, a self delivery wearing any other badge and an
 * arc do not, and only the channeled or held self runs on the player
 * (decisions stream-delivery-held-cone,
 * flatten-disc-cursor-breaks-above-the-plane,
 * held-badge-marks-abilities-that-prepare-and-throw).
 */
class HeldRouteTest {

    private static final Delivery SELF = Delivery.of(DeliveryKind.SELF);
    private static final Delivery STREAM = Delivery.of(DeliveryKind.STREAM);

    /**
     * decision residual-badge-marks-the-four-goo-sprays
     *
     * @param badge the stream's badge
     */
    @ParameterizedTest
    @EnumSource(value = AbilityBadge.class, names = {"CHANNELED", "RESIDUAL"})
    void streamRunsWhileHeldButIsNoChannel(AbilityBadge badge) {
        assertTrue(HeldRoute.runsWhileHeld(STREAM, badge));
        assertFalse(HeldRoute.channelsOnSelf(STREAM, badge));
    }

    @Test
    void channeledSelfRunsWhileHeldOnThePlayer() {
        assertTrue(HeldRoute.runsWhileHeld(SELF, AbilityBadge.CHANNELED));
        assertTrue(HeldRoute.channelsOnSelf(SELF, AbilityBadge.CHANNELED));
    }

    // held-badge-marks-abilities-that-prepare-and-throw
    @Test
    void heldSelfRunsWhileHeldOnThePlayer() {
        assertTrue(HeldRoute.runsWhileHeld(SELF, AbilityBadge.HELD));
        assertTrue(HeldRoute.channelsOnSelf(SELF, AbilityBadge.HELD));
    }

    @Test
    void selfWearingTheSelfBadgeRunsOnCommand() {
        assertFalse(HeldRoute.runsWhileHeld(SELF, AbilityBadge.SELF));
    }

    /**
     * The shipped Scry is cast once on a press, wearing the instant badge
     * over a self delivery that charges nothing per held tick.
     * decision scry-is-instant-and-no-longer-channeled
     */
    @Test
    void theShippedScryIsAnInstantSelfAbilityRunOnCommand() {
        AbilityDefinition scry = AbilityJson.decode("astral_scry");

        assertEquals(AbilityBadge.SELF, scry.badge(), "the instant badge");
        assertEquals(DeliveryKind.SELF, scry.delivery().kind());
        assertFalse(scry.delivery().charges(), "a press charges nothing per held tick");
        assertFalse(HeldRoute.channelsOnSelf(scry.delivery(), scry.badge()), "no channel on the player");
        assertFalse(HeldRoute.runsWhileHeld(scry.delivery(), scry.badge()), "nothing runs while held");
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
