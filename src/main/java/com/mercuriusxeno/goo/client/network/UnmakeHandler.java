package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.ClientSoups;
import com.mercuriusxeno.goo.client.ability.MeltingBlocks;
import com.mercuriusxeno.goo.client.ability.MorphingRemains;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.network.SoupPayload;
import com.mercuriusxeno.goo.network.UnmadePayload;
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
     * Handles the unmade payload on the client thread: the remains begin
     * morphing into the goo item.
     *
     * @param payload the unmade payload
     * @param context the network context
     */
    public static void handleUnmade(UnmadePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            MorphingRemains.CLIENT.begin(payload.at(), new GooContents(payload.goo()), payload.size(),
                    payload.squat(), mc.level.getGameTime());
        });
    }

    /**
     * Keeps a player's Unmake soup as the server shows it
     * (decision unmake-waves-dissolve-by-crucible-cost).
     *
     * @param payload the soup payload
     * @param context the network context
     */
    public static void handleSoup(SoupPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                ClientSoups.CLIENT.show(payload, mc.level.getGameTime());
            }
        });
    }
}
