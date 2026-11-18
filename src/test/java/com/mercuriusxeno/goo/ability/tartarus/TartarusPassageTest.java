package com.mercuriusxeno.goo.ability.tartarus;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Covers the transfer Tartarus builds to carry a player to the spot its placement answered.
 */
class TartarusPassageTest {

    private static final BlockPos SPOT = new BlockPos(101, 64, -199);
    private static final float YAW = 45f;

    /** decision tartarus-makes-no-nether-portal-and-touches-no-portal-linkage */
    @Test
    void theTransferCarriesThePlacementsSpotAsItsExactPosition() {
        ServerLevel nether = mock(ServerLevel.class);

        TeleportTransition transfer = TartarusPassage.transitionTo(nether, SPOT, YAW);

        assertSame(nether, transfer.newLevel());
        assertEquals(new Vec3(101.5, 64, -198.5), transfer.position());
        assertTrue(transfer.relatives().isEmpty(), "no coordinate is relative");
        assertEquals(Vec3.ZERO, transfer.deltaMovement());
        assertEquals(YAW, transfer.yRot());
    }
}
