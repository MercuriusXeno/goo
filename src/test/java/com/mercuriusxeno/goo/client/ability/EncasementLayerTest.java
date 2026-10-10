package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The encasement's vertex color: the share rides its alpha and the mob's
 * seed its red, green and blue, different mobs seeding differently.
 */
class EncasementLayerTest {

    @Test
    void theShareRidesTheAlpha() {
        assertEquals(255, ARGB.alpha(EncasementLayer.shareColor(1f, EncasementLayer.seedOf(7))));
        assertEquals(0, ARGB.alpha(EncasementLayer.shareColor(0f, EncasementLayer.seedOf(7))));
    }

    @Test
    void theSeedRidesTheColor() {
        int seed = EncasementLayer.seedOf(42);
        assertEquals(seed, EncasementLayer.shareColor(0.5f, seed) & 0xFFFFFF);
    }

    @Test
    void neighbouringMobsSeedApart() {
        assertNotEquals(EncasementLayer.seedOf(41), EncasementLayer.seedOf(42));
        assertNotEquals(EncasementLayer.seedOf(41) & 0xFF0000, EncasementLayer.seedOf(42) & 0xFF0000);
    }
}
