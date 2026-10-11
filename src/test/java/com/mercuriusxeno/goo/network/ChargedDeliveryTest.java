package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.ChargedMultipliers;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A charged stream sprays its cone scaled by its ability's area multiplier,
 * kept within a half turn, and an uncharged one sprays it as written
 * (decision charged-scales-channel-params-by-json).
 */
class ChargedDeliveryTest {

    private static final double DELTA = 1e-9;
    private final Delivery spitfire = AbilityJson.decode("blaze_spitfire").delivery();

    @Test
    void anUnchargedStreamSpraysAsWritten() {
        assertSame(spitfire, GooStreamHandler.chargedDelivery(spitfire, ChargedMultipliers.NONE));
    }

    @Test
    void theAreaMultiplierLengthensAndWidensTheCone() {
        Delivery charged = GooStreamHandler.chargedDelivery(spitfire, new ChargedMultipliers(1.5, 1, 1, 1));

        assertEquals(spitfire.range() * 1.5, charged.range(), DELTA);
        assertEquals(spitfire.coneDegrees() * 1.5, charged.coneDegrees(), DELTA);
        assertEquals(spitfire.ticksPerCharge(), charged.ticksPerCharge());
    }

    @Test
    void theConeOpensNoWiderThanAHalfTurn() {
        Delivery charged = GooStreamHandler.chargedDelivery(spitfire, new ChargedMultipliers(100, 1, 1, 1));

        assertEquals(180.0, charged.coneDegrees(), DELTA);
    }
}
