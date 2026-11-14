package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the shipped mob abilities cast like Charm: each names the envelop
 * delivery with no charge holding back the release, and Rewind wears the mob badge
 * (decision charm-like-abilities-lock-the-highlighted-mob-on-right-click).
 */
class CharmLikeAbilitiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"hex_charm", "quantum_phase_other", "zoo_rally", "zoo_shape", "zoo_spawn", "typhoon_shulk",
            "yore_rewind"})
    void namesTheEnvelopDeliveryWithNoWindUp(String ability) {
        Delivery delivery = AbilityJson.decode(ability).delivery();
        assertTrue(delivery.envelops() && !delivery.charges(),
                ability + " delivers by " + delivery.kind() + " winding up " + delivery.chargeTicks() + " ticks");
    }

    /** Rewind leaves the channeled badge for the mob badge the other charm-like casts wear. */
    @Test
    void rewindWearsTheMobBadge() {
        assertEquals(AbilityBadge.MOB, AbilityJson.decode("yore_rewind").badge());
    }
}
