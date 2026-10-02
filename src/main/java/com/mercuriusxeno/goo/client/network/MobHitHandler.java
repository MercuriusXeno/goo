package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.MobCoats;
import com.mercuriusxeno.goo.client.ability.MobHitBurst;
import com.mercuriusxeno.goo.network.MobHitPayload;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client-side handler for a goo landing on a mob: coats the mob in the goo
 * type and bursts its drips off the struck point. Throw, touch and punch
 * arrive through the one payload alike.
 * Decision hit-bursts-goo-particles.
 * Decision shader-coat-on-every-mob-landing.
 */
public final class MobHitHandler {

    private MobHitHandler() {}

    /**
     * Handles the mob-hit payload on the client thread.
     *
     * @param payload the mob-hit payload
     * @param context the network context
     */
    public static void handle(MobHitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            ResourceKey<GooTypeDefinition> gooType = GooTypes.byId(payload.gooTypeId());
            if (mc.level == null || gooType == null) {
                return;
            }
            MobCoats.CLIENT.coat(payload.entityId(), gooType, mc.level.getGameTime(), payload.hitPoint());
            Entity struck = mc.level.getEntity(payload.entityId());
            // A mob the client no longer holds splashes straight up off the struck point.
            Vec3 center = struck == null ? payload.hitPoint().subtract(0, 1, 0)
                    : struck.getBoundingBox().getCenter();
            MobHitBurst.spawn(mc.level, payload.hitPoint(), center,
                    GooColors.get(mc.level.registryAccess(), gooType));
        });
    }
}
