package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers which mark floats over a mob's head: Charm's heart, Lead's fist.
 */
class HeadMarkTest {

    @Test
    void ledMobWearsTheFist() {
        assertEquals(Optional.of(HeadMark.LEAD_FIST), HeadMark.of(false, true));
    }

    @Test
    void fistDrawsTheLeadFistTexture() {
        assertEquals(Identifier.fromNamespaceAndPath(Goo.MODID, "textures/entity/lead_fist.png"),
                HeadMark.LEAD_FIST.texture());
    }

    @Test
    void charmedMobWearsTheHeart() {
        assertEquals(Optional.of(HeadMark.CHARMED_HEART), HeadMark.of(true, false));
    }

    @Test
    void charmedAndLedMobWearsTheHeart() {
        assertEquals(Optional.of(HeadMark.CHARMED_HEART), HeadMark.of(true, true));
    }

    @Test
    void unmarkedMobWearsNoMark() {
        assertEquals(Optional.empty(), HeadMark.of(false, false));
    }
}
