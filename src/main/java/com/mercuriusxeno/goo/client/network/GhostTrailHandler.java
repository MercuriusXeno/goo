package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.GhostTrails;
import com.mercuriusxeno.goo.network.GhostTrailPayload;
import com.mercuriusxeno.goo.type.GooColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for a ghost trail: snapshots the entity's render
 * state as it stands now, the pose it held the tick it blinked, and lays
 * the trail of ghosts in that pose from the jump's source to its
 * destination in the goo type's color.
 * Decision ghost-trail-spans-the-blink.
 */
public final class GhostTrailHandler {

    private GhostTrailHandler() {}

    /**
     * Handles the ghost trail payload on the client thread.
     *
     * @param payload the ghost trail payload
     * @param context the network context
     */
    public static void handle(GhostTrailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            EntityRenderState snapshot = EntitySnapshots.of(mc, payload.entityId());
            if (mc.level == null || snapshot == null) {
                return;
            }
            int rgb = GooColors.get(mc.level.registryAccess(), payload.gooType());
            GhostTrails.CLIENT.add(snapshot, payload.source(), payload.destination(), rgb, mc.level.getGameTime(),
                    payload.lifeTicks());
        });
    }
}
