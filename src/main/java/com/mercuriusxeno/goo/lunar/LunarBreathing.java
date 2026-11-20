package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.typhoon.PneumaBreathing;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;

/**
 * Each breath a living thing takes, as NeoForge's breathe event hands it.
 * A player in lunar breathes as one whose eyes are under water: the air
 * drains a breath a tick, drowning lands when it runs out, and water
 * breathing, a turtle helmet and an invulnerable player hold it as they do
 * underwater; a mount carries its rider across lunar, since only a real
 * fluid dismounts. A player's Pneuma answers each breath taken while the
 * player cannot breathe, before the vanilla air does.
 * decision astral-air-drains-as-underwater
 * pneuma-overfills-the-air-meter-with-a-brief-airless-immunity
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class LunarBreathing {

    private LunarBreathing() {
    }

    /**
     * Drains a lunar player's air as under water, then lets Pneuma cover the breath.
     *
     * @param event the breathe event
     */
    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        LivingEntity living = event.getEntity();
        if (Lunar.isAirless(living)) {
            boolean holds = holdsTheAirUnderwater(living);
            event.setCanBreathe(holds);
            if (holds) {
                event.setRefillAirAmount(0);
            }
        }
        if (!event.canBreathe() && living instanceof Player player && PneumaBreathing.coversBreath(player)) {
            event.setConsumeAirAmount(0);
        }
    }

    // NeoForge's own test of whether eyes under water keep their air.
    private static boolean holdsTheAirUnderwater(LivingEntity living) {
        return MobEffectUtil.hasWaterBreathing(living) || !living.canDrownInFluidType(NeoForgeMod.WATER_TYPE.value())
                || living instanceof Player player && player.getAbilities().invulnerable;
    }
}
