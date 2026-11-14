package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the shipped mob abilities cast like Charm: each names the envelop
 * delivery and a wind-up its charge fills before the release sends, and
 * wears the mob badge (decision charm-like-abilities-lock-the-highlighted-mob-on-right-click).
 */
class CharmLikeAbilitiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"hex_charm", "quantum_phase_other", "zoo_rally", "zoo_shape", "zoo_spawn", "typhoon_shulk",
            "yore_rewind"})
    void namesTheEnvelopDeliveryWithAWindUp(String ability) {
        Delivery delivery = AbilityJson.decode(ability).delivery();
        assertTrue(delivery.envelops() && delivery.chargeTicks() > 0,
                ability + " delivers by " + delivery.kind() + " winding up " + delivery.chargeTicks() + " ticks");
    }

    /** Rewind leaves the channeled badge for the one a charged mob ability wears. */
    @Test
    void rewindWearsTheMobBadge() {
        assertEquals(AbilityBadge.MOB, AbilityJson.decode("yore_rewind").badge());
    }
}
