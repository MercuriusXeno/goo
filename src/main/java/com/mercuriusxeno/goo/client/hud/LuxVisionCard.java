package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.held.LuxEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Hides Lux's own night vision from the HUD and the inventory, so Lux shows
 * no night vision card; any other night vision, a potion's or a beacon's,
 * shows as vanilla draws it (operator ruling 2026-10-09).
 * decision lux-night-vision-without-particles
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LuxVisionCard implements IClientMobEffectExtensions {

    private LuxVisionCard() {
    }

    /**
     * Registers the card rule on night vision.
     *
     * @param event the client extensions registration event
     */
    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {
        event.registerMobEffect(new LuxVisionCard(), MobEffects.NIGHT_VISION.value());
    }

    @Override
    public boolean isVisibleInInventory(MobEffectInstance instance) {
        return !LuxEvents.isLuxVision(instance);
    }

    @Override
    public boolean isVisibleInGui(MobEffectInstance instance) {
        return !LuxEvents.isLuxVision(instance);
    }
}
