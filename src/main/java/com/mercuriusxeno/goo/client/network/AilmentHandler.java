package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.MobAilments;
import com.mercuriusxeno.goo.network.AilmentPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for a status ailment landing on an entity: the
 * entity wears the ailment's overlay until it runs out.
 * Decision ailment-overlay-shader-per-ailment.
 */
public final class AilmentHandler {

    private AilmentHandler() {}

    /**
     * Handles the ailment payload on the client thread.
     *
     * @param payload the ailment payload
     * @param context the network context
     */
    public static void handle(AilmentPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            MobAilments.CLIENT.afflict(payload.entityId(), payload.kind(), mc.level.getGameTime(),
                    payload.durationTicks());
        });
    }
}
