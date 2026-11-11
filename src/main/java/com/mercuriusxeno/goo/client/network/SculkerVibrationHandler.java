package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.SculkerSenses;
import com.mercuriusxeno.goo.network.SculkerVibrationPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for a vibration the server forwarded to a diver:
 * lends its frequency to the sound its noise played.
 * decision sculker-echo-intensity-follows-vibration-rank
 */
public final class SculkerVibrationHandler {

    private SculkerVibrationHandler() {}

    /**
     * Handles the vibration payload on the client thread.
     *
     * @param payload the vibration payload
     * @param context the network context
     */
    public static void handle(SculkerVibrationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SculkerSenses.feel(new Vec3(payload.x(), payload.y(), payload.z()), payload.rank()));
    }
}
