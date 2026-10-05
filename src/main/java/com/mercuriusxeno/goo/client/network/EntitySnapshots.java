package com.mercuriusxeno.goo.client.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Freezes a living entity's render state as it stands this frame, so an
 * effect can draw the entity in that pose after it has moved on: the
 * afterimage's ripple and the ghost trail take their poses from here.
 * Decisions afterimage-is-one-shared-effect, ghost-trail-spans-the-blink.
 */
final class EntitySnapshots {

    private EntitySnapshots() {}

    /**
     * The render state of the living entity an id names, extracted fresh.
     *
     * @param mc       the client, its level loaded
     * @param entityId the entity's id
     * @return the frozen render state, or null where the client holds no living entity by that id
     */
    static @Nullable EntityRenderState of(Minecraft mc, int entityId) {
        Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
        if (!(entity instanceof LivingEntity)) {
            return null;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return mc.getEntityRenderDispatcher().extractEntity(entity, partialTick);
    }
}
