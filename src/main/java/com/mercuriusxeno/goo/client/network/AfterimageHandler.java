package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.Afterimages;
import com.mercuriusxeno.goo.network.AfterimagePayload;
import com.mercuriusxeno.goo.type.GooColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for an afterimage: snapshots the entity's render
 * state as it stands now, so the echo keeps that pose, and leaves it at the
 * payload's point in the goo type's color.
 * Decision afterimage-is-one-shared-effect.
 */
public final class AfterimageHandler {

    private AfterimageHandler() {}

    /**
     * Handles the afterimage payload on the client thread.
     *
     * @param payload the afterimage payload
     * @param context the network context
     */
    public static void handle(AfterimagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Entity entity = mc.level.getEntity(payload.entityId());
            if (!(entity instanceof LivingEntity)) {
                return;
            }
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            EntityRenderState snapshot = mc.getEntityRenderDispatcher().extractEntity(entity, partialTick);
            int rgb = GooColors.get(mc.level.registryAccess(), payload.gooType());
            Afterimages.CLIENT.add(snapshot, payload.position(), rgb, mc.level.getGameTime(), payload.lifeTicks());
        });
    }
}
