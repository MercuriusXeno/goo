package com.mercuriusxeno.goo.entity;

import net.minecraft.world.entity.MoveSimulationType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * A compression sphere moves itself on each side its tick runs, the server's and each client's, as 26.2's sphere
 * did: Minecraft 26.3 lets an entity move itself only on a side its move simulation names, so the sphere names both,
 * as the game's own dropped items and projectiles do.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
class CompressionSphereMotionTest {

    @Test
    void theSphereSimulatesItsMotionOnTheServerAndEachClient() {
        CompressionSphere sphere = mock(CompressionSphere.class, CALLS_REAL_METHODS);
        assertEquals(MoveSimulationType.SERVER_AND_CLIENT, sphere.getMoveSimulationType());
    }
}
