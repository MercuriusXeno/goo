package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The shipped abilities whose lifted blob floats to the center line: Click,
 * Orb, Drain, Reap, Zap, Glimmer and Magnet, and no other; Bore's blob rests
 * by the glove.
 * centered-abilities-float-the-blob-below-the-crosshair
 * bore-blob-lifts-by-the-glove-and-the-drill-extends-from-that-hand
 */
class CenteredAbilitiesTest {

    @Test
    void theShippedCenteredAbilitiesAreTheSevenTheOperatorNamed() {
        Set<String> centered = AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ability -> ability.hasTag(AbilityTags.CENTERED))
                .map(ability -> ability.id().getPath()).collect(Collectors.toSet());

        assertEquals(Set.of("deep_click", "frost_orb", "hex_drain", "leaf_reap", "pulse_zap",
                "crystal_glimmer", "metal_magnet"), centered);
    }
}
