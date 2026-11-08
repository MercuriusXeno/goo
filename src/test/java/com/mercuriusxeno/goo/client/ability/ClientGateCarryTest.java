package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.GateCarriedPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A body the server carried through a linked gate is set where it came out
 * at once on the client, never sliding there through the walls, and keeps
 * moving out along its motion until the server's next move reaches the
 * client, so it never stalls at the partner's face; the server's own stale
 * word placing it back at the landing is dropped meanwhile
 * (decision aim-and-shots-carry-through-linked-gates).
 */
class ClientGateCarryTest {

    private static final int BODY = 7;
    private static final long CARRIED_AT = 61402;
    private static final GateCarriedPayload CARRY = new GateCarriedPayload(BODY, new Vec3(480.5, 64.0, 3.2),
            new Vec3(-0.23, 0, 0), 90F, 10F);

    @Test
    void aCarriedBodyIsSetWhereItCameOutAndMovesOn() {
        Entity body = mock(Entity.class);
        InterpolationHandler slide = mock(InterpolationHandler.class);
        when(body.getInterpolation()).thenReturn(slide);

        ClientGateCarry.snap(body, CARRY, CARRIED_AT);

        verify(slide).cancel();
        verify(body).snapTo(CARRY.feet(), CARRY.yaw(), CARRY.pitch());
        verify(slide).interpolateTo(CARRY.feet().add(CARRY.motion().scale(ClientGateCarry.LEAD_TICKS)), CARRY.yaw(),
                CARRY.pitch());
    }

    @Test
    void theServersWordPlacingTheBodyBackAtTheLandingIsStale() {
        ClientGateCarry.snap(mock(Entity.class), CARRY, CARRIED_AT);

        assertTrue(ClientGateCarry.staleSync(BODY, CARRY.feet().add(0.0001, 0, 0), CARRIED_AT + 2),
                "A move placing the body back at its landing just after the carry should be dropped");
        assertFalse(ClientGateCarry.staleSync(BODY, CARRY.feet().add(-0.7, 0, 0), CARRIED_AT + 2),
                "A move the body made since the carry should stand");
        assertFalse(ClientGateCarry.staleSync(BODY, CARRY.feet(), CARRIED_AT + 40),
                "A move long after the carry should stand, wherever it places the body");
    }
}
