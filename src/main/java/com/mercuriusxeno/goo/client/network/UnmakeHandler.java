package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.ClientDrinks;
import com.mercuriusxeno.goo.client.ability.MorphingRemains;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.network.DrinkPayload;
import com.mercuriusxeno.goo.network.UnmadePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for Unmake's payloads: the drink as the server shows it,
 * and the remains of anything unmade.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakeHandler {

    private UnmakeHandler() {}

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
                    mc.level.getGameTime());
        });
    }

    /**
     * Keeps a player's Unmake drink as the server shows it
     * (decision unmake-waves-dissolve-by-crucible-cost).
     *
     * @param payload the drink payload
     * @param context the network context
     */
    public static void handleDrink(DrinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                ClientDrinks.CLIENT.show(payload);
            }
        });
    }
}
