package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which held throws a left click throws from the hold the server keeps: Knives'
 * and Dart's alike, so a throw leaves the hold standing, forming on beneath it,
 * and a held left click keeps throwing on its beat
 * (decisions dart-throws-one-javelin-per-five-ticks-while-left-click-is-held,
 * dart-stacks-three-javelins-one-per-ten-ticks, knives-form-on-right-click-and-throw-on-left-click).
 */
class KnivesHoldSenderTest {

    private static Delivery deliveryOf(String ability) {
        return AbilityJson.decode(ability).delivery();
    }

    @Test
    void knivesThrowFromTheHold() {
        assertTrue(KnivesHoldSender.throwsFromTheHold(deliveryOf("crystal_knives"), AbilityBadge.HELD));
    }

    @Test
    void dartThrowsFromTheHoldSoAThrowKeepsTheJavelinsStillHeld() {
        assertTrue(KnivesHoldSender.throwsFromTheHold(deliveryOf("metal_dart"), AbilityBadge.HELD),
                "Dart's left click should throw from the server's standing hold, not end it and begin another");
    }
}
