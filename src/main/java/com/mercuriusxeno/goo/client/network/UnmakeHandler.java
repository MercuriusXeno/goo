package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.MeltingBlocks;
import com.mercuriusxeno.goo.client.ability.MeltingMobs;
import com.mercuriusxeno.goo.network.UnmakeMobPayload;
import com.mercuriusxeno.goo.network.UnmakePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for unmake shares: records each against its block or mob,
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
            MeltingBlocks.CLIENT.record(payload.pos(), payload.fraction(), mc.level.getGameTime());
        });
    }

    /**
     * Handles the mob unmake payload on the client thread.
     *
     * @param payload the mob unmake payload
     * @param context the network context
     */
    public static void handleMob(UnmakeMobPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            MeltingMobs.CLIENT.record(payload.entityId(), payload.fraction(), mc.level.getGameTime());
        });
    }
}
