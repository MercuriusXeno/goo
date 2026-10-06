package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.GooSelfHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Runs a goo type's brew ability when its brew effect lands on a player, for
 * the duration the landing instance carries. Potions register at boot, before
 * any ability JSON loads, so the ability is looked up here, when drunk.
 * decision brew-grants-the-self-ability-for-an-hour
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class GooBrewEffectEvents {

    private GooBrewEffectEvents() {
    }

    /**
     * Runs the brew ability of a goo brew effect added to a server player.
     *
     * @param event the effect added
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance.getEffect().value() instanceof GooBrewEffect brew
                && event.getEntity() instanceof ServerPlayer player) {
            GooSelfHandler.drinkBrew(player, brew.gooType(), instance.getDuration());
        }
    }
}
