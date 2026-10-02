package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.client.ability.MobCoats;
import com.mercuriusxeno.goo.client.ability.MobHitBurst;
import com.mercuriusxeno.goo.client.ability.SplatDrips;
import com.mercuriusxeno.goo.network.MobHitPayload;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
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
            int rgb = GooColors.get(mc.level.registryAccess(), gooType);
            Entity struck = mc.level.getEntity(payload.entityId());
            if (struck == null) {
                // A mob the client no longer holds wears no splat; its hit splashes straight up.
                MobHitBurst.spawn(mc.level, payload.hitPoint(), payload.hitPoint().subtract(0, 1, 0), rgb);
                return;
            }
            MobCoats.CLIENT.coat(payload.entityId(), gooType, mc.level.getGameTime(),
                    new MobCoats.Strike(payload.hitPoint(), payload.aimDirection(), SplatDrips.stanceOf(struck)));
            MobHitBurst.spawn(mc.level, payload.hitPoint(), struck.getBoundingBox().getCenter(), rgb);
        });
    }
}
