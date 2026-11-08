package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.GateCarriedPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InterpolationHandler;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sets a body the server carried through a linked gate where it came out,
 * at once: the vanilla teleport a client hears for a mob in sight slides it
 * there over a few ticks, through the walls between the gate it went into
 * and the partner it came out of, drawn dark while inside them.
 * aim-and-shots-carry-through-linked-gates
 */
public final class ClientGateCarry {

    private ClientGateCarry() {
    }

    /**
     * Handles a carry on the client thread.
     *
     * @param payload the carry
     * @param context the network context
     */
    public static void handle(GateCarriedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            Entity body = mc.level == null ? null : mc.level.getEntity(payload.entityId());
            if (body != null) {
                snap(body, payload);
            }
        });
    }

    /**
     * Sets a carried body where it came out.
     *
     * @param body  the body
     * @param carry where and how it came out
     */
    static void snap(Entity body, GateCarriedPayload carry) {
        InterpolationHandler slide = body.getInterpolation();
        if (slide != null) {
            slide.cancel();
        }
        body.snapTo(carry.feet(), carry.yaw(), carry.pitch());
        body.setDeltaMovement(carry.motion());
    }
}
