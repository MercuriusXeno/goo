package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.DissolvingBlocks;
import com.mercuriusxeno.goo.network.UnmakePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for unmake shares: records each against its block,
 * stamped with the game time it arrived.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakeHandler {

    private UnmakeHandler() {}

    /**
     * Handles the unmake payload on the client thread.
     *
     * @param payload the unmake payload
     * @param context the network context
     */
    public static void handle(UnmakePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            DissolvingBlocks.CLIENT.record(payload.pos(), payload.fraction(), mc.level.getGameTime());
        });
    }
}
