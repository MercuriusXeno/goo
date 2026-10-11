package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.LurkerPulses;
import com.mercuriusxeno.goo.network.LurkerPulsePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for lurker pulses: records each against its marker,
 * stamped with the game time it arrived.
 * decision lurker-blob-brightens-then-detonates
 */
public final class LurkerPulseHandler {

    private LurkerPulseHandler() {}

    /**
     * Handles the pulse payload on the client thread.
     *
     * @param payload the pulse payload
     * @param context the network context
     */
    public static void handle(LurkerPulsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            LurkerPulses.CLIENT.record(payload.pos(), payload.closeness(), mc.level.getGameTime());
        });
    }
}
