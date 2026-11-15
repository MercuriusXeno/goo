package com.mercuriusxeno.goo.client.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the rule turning a cell's layers into its slime core's box, in
 * fifteenths of a block: two layers of goo stand between the core and the
 * ground, the surface and each side.
 */
class SlimeCoreBoxTest {

    @Nested
    class LoneCell {

        @Test
        void fourLayersHoldNoCore() {
            assertEquals(Optional.empty(), SlimeCoreBox.of(4));
        }

        @Test
        void fiveLayersHoldACoreOneLayerThick() {
            assertEquals(Optional.of(new SlimeCoreBox(2, 2, 2, 13, 3, 13)), SlimeCoreBox.of(5));
        }

        @Test
        void eachLayerPastFiveAddsALayerToTheCore() {
            assertEquals(Optional.of(new SlimeCoreBox(2, 2, 2, 13, 6, 13)), SlimeCoreBox.of(8));
        }

        @Test
        void aWholeBlockHoldsACoreTwoLayersInFromEveryFace() {
            assertEquals(Optional.of(new SlimeCoreBox(2, 2, 2, 13, 13, 13)), SlimeCoreBox.of(15));
        }
    }
}
