package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.NovaRings;
import com.mercuriusxeno.goo.network.NovaRingPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for a frost nova pulsed nearby: its ring spreads from
 * the caster's feet to the reach its charge resolved.
 * Decision nova-ring-grows-with-the-hold.
 */
public final class NovaRingHandler {

    private NovaRingHandler() {}

    /**
     * Handles the nova payload on the client thread.
     *
     * @param payload the nova payload
     * @param context the network context
     */
    public static void handle(NovaRingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                NovaRings.CLIENT.pulse(mc.level, payload.center(), payload.reach());
            }
        });
    }
}
