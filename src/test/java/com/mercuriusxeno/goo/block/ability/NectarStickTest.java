package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.NectarGooStep;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers Nectar's nerf: an entity touching the goo jumps and moves at the
 * jump and speed factors jelly_nectar.json ships, and at full strength once
 * clear of it.
 * nectar-is-the-sticky-goo-you-climb-that-nerfs-jumping-and-speed
 */
class NectarStickTest {

    private static final NectarGooStep SHIPPED = NectarGooStep.in(AbilityJson.decode("jelly_nectar").behaviors())
            .orElseThrow();
    private static final double FULL_STRENGTH = 1.0;

    @Test
    void anEntityTouchingNectarJumpsAndMovesAtTheShippedFactors() {
        assertEquals(new NectarStick.Multipliers(SHIPPED.jumpFactor(), SHIPPED.speedFactor()),
                NectarStick.multipliersFor(true, SHIPPED));
    }

    @Test
    void anEntityClearOfNectarJumpsAndMovesAtFullStrength() {
        assertEquals(new NectarStick.Multipliers(FULL_STRENGTH, FULL_STRENGTH),
                NectarStick.multipliersFor(false, SHIPPED));
    }
}
