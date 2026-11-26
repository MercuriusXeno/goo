package com.mercuriusxeno.goo.registry;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The mod's block register stands without the ability block a thrown ability
 * once left at its landing (decision ability-marker-leaves-the-mod-whole).
 */
class GooBlocksTest {

    private static final Identifier RETIRED_MARKER = Identifier.fromNamespaceAndPath("goo", "ability_block");

    // decision ability-marker-leaves-the-mod-whole
    @Test
    void theBlockRegisterHoldsNoAbilityMarker() {
        assertFalse(GooBlocks.BLOCKS.getEntries().stream().map(DeferredHolder::getId).anyMatch(RETIRED_MARKER::equals),
                RETIRED_MARKER + " should leave the block register");
    }
}
