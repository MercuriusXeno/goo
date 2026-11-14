package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.overlay.OreSightings;
import com.mercuriusxeno.goo.network.OreRevealPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for the ore veins a held sweep of Glimmer or Magnet
 * reached: each shows through walls for its life.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision glitter-sphere-icons-gem-ore-groups
 */
public final class OreRevealHandler {

    private OreRevealHandler() {}

    /**
     * Handles the reveal payload on the client thread.
     *
     * @param payload the reveal payload
     * @param context the network context
     */
    public static void handle(OreRevealPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                OreSightings.CLIENT.reveal(mc.level.getGameTime(), payload.veins(), payload.life());
            }
        });
    }
}
