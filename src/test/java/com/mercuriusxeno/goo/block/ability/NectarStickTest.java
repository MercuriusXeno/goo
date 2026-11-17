package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.NectarGooStep;
import org.junit.jupiter.api.Test;
import java.util.List;

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

    /**
     * operator UAT 2026-11-17: on Nectar you are temporarily Spider-Man, gravity off while stuck; jumping ascends,
     * sneaking descends
     */
    @Test
    void anEntityStuckOnAWallRisesWhileJumpingSinksWhileSneakingAndHangsOtherwise() {
        double climb = SHIPPED.climbSpeed();

        assertEquals(List.of(climb, -climb, 0.0, 0.0), List.of(NectarStick.climbVelocity(true, false, climb),
                NectarStick.climbVelocity(false, true, climb), NectarStick.climbVelocity(false, false, climb),
                NectarStick.climbVelocity(true, true, climb)));
    }

    @Test
    void anEntityClearOfNectarJumpsAndMovesAtFullStrength() {
        assertEquals(new NectarStick.Multipliers(FULL_STRENGTH, FULL_STRENGTH),
                NectarStick.multipliersFor(false, SHIPPED));
    }
}
