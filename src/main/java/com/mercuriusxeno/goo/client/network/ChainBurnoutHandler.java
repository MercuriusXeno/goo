package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.ability.ChainBurnouts;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for chain marker burnouts: adds each to the live
 * explosion list, stamped with the game time it began (decision
 * elemental-explosion-per-type).
 */
public final class ChainBurnoutHandler {

    private ChainBurnoutHandler() {}

    /**
     * Handles the burnout payload on the client thread.
     *
     * @param payload the burnout payload
     * @param context the network context
     */
    public static void handle(ChainBurnoutPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            ResourceKey<GooTypeDefinition> gooType = GooTypes.byId(payload.gooTypeId());
            if (mc.level == null || gooType == null) {
                return;
            }
            Direction[] faces = Direction.values();
            Direction face = faces[Math.floorMod(payload.placedFace(), faces.length)];
            ChainBurnouts.Burnout burnout = ChainBurnouts.CLIENT.add(payload.pos(), face, gooType,
                    payload.abilityId(), payload.stackCount(), mc.level.getGameTime());
            if (burnout != null) {
                burnout.visual().begin(burnout, mc.level);
            }
        });
    }
}
