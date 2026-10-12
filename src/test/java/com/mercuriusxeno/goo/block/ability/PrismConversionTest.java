package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bare prism's charge converts it at the hold of unbroken stream and not
 * before, and a break or another type starts it again
 * (decision channeled-hosts-convert-prisms-over-the-hold).
 */
class PrismConversionTest {

    private static final int HOLD = 40;
    private static final ResourceKey<GooTypeDefinition> PULSE =
            ResourceKey.create(GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("goo", "pulse"));
    private static final ResourceKey<GooTypeDefinition> DECAY =
            ResourceKey.create(GooTypes.REGISTRY, Identifier.fromNamespaceAndPath("goo", "decay"));

    private static PrismConversion heldFor(int ticks, long from) {
        PrismConversion conversion = PrismConversion.NONE;
        for (int tick = 0; tick < ticks; tick++) {
            conversion = conversion.chargedBy(PULSE, from + tick);
        }
        return conversion;
    }

    @Test
    void aHoldShortOfTheChargeDoesNotConvert() {
        assertFalse(heldFor(HOLD - 1, 0).converts(HOLD));
    }

    @Test
    void theHoldConverts() {
        assertTrue(heldFor(HOLD, 0).converts(HOLD));
    }

    @Test
    void aMissedTickStartsTheChargeAgain() {
        PrismConversion broken = heldFor(HOLD - 1, 0).chargedBy(PULSE, HOLD + 1);

        assertEquals(1, broken.charge());
    }

    @Test
    void anotherTypeStartsTheChargeAgain() {
        assertEquals(1, heldFor(HOLD - 1, 0).chargedBy(DECAY, HOLD - 1).charge());
    }
}
