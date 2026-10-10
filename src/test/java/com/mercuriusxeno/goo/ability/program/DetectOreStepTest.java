package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Glitter's ore sense pings on a hold's first tick and every ping after,
 * revealing each vein the tick the front reaches it
 * (decision glitter-sphere-icons-gem-ore-groups).
 */
class DetectOreStepTest {

    private final DetectOreStep glitter = new DetectOreStep(
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "gem_ores")), 24, 1, 32, 100);

    @Test
    void aPingStartsOnTheFirstTickAndEveryPingAfter() {
        assertTrue(glitter.pingsOn(1));
        assertFalse(glitter.pingsOn(2));
        assertFalse(glitter.pingsOn(32));
        assertTrue(glitter.pingsOn(33));
    }

    @Test
    void theFrontReachesAVeinAtItsDistanceOverTheGrowth() {
        assertEquals(0, glitter.revealTick(0));
        assertEquals(11, glitter.revealTick(10.2));
    }
}
