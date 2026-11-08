package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.SculkerSenses;
import com.mercuriusxeno.goo.network.SculkerHeardPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for a far sound the server carried to a
 * diver: hears it as a sound the game plays.
 * decision sculker-sight-persists-where-echoes-have-touched
 */
public final class SculkerHeardHandler {

    private SculkerHeardHandler() {}

    /**
     * Handles the heard payload on the client thread.
     *
     * @param payload the heard payload
     * @param context the network context
     */
    public static void handle(SculkerHeardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SculkerSenses.hear(new Vec3(payload.x(), payload.y(), payload.z()), payload.loudness()));
    }
}
