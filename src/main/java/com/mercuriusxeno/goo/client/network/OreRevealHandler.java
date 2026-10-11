package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.GlitterShell;
import com.mercuriusxeno.goo.client.overlay.OreIcons;
import com.mercuriusxeno.goo.network.OreRevealPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for a Glitter ping: its sparkle shell grows, and its
 * veins show as the shell's front reaches them.
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
                long now = mc.level.getGameTime();
                GlitterShell.start(payload, now);
                OreIcons.CLIENT.reveal(now, payload.veins(), payload.reveal(), payload.life());
            }
        });
    }
}
