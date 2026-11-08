package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.GateCarriedPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A body the server carried through a linked gate is set where it came out
 * at once on the client, its slide toward any earlier spot dropped, so it
 * never slides from the gate it went into through the walls between
 * (decision aim-and-shots-carry-through-linked-gates).
 */
class ClientGateCarryTest {

    private static final GateCarriedPayload CARRY = new GateCarriedPayload(7, new Vec3(480.5, 64.0, 3.2),
            new Vec3(0, -0.2, 0.3), 90F, 10F);

    @Test
    void aCarriedBodyIsSetWhereItCameOutAtOnce() {
        Entity body = mock(Entity.class);
        InterpolationHandler slide = mock(InterpolationHandler.class);
        when(body.getInterpolation()).thenReturn(slide);

        ClientGateCarry.snap(body, CARRY);

        verify(slide).cancel();
        verify(body).snapTo(CARRY.feet(), CARRY.yaw(), CARRY.pitch());
        verify(body).setDeltaMovement(CARRY.motion());
    }
}
