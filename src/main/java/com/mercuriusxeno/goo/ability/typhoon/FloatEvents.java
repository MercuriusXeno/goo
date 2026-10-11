package com.mercuriusxeno.goo.ability.typhoon;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import org.jspecify.annotations.Nullable;

/**
 * Clears a mob's float the moment its levitation ends, run out or removed by
 * milk, so the platform under its feet never outlasts the lift.
 * float-blob-levitates-the-mob
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class FloatEvents {

    private FloatEvents() {
    }

    /**
     * Clears the float when levitation runs out.
     *
     * @param event the expired effect
     */
    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        clearWhenLevitationEnds(event.getEntity(), instance == null ? null : instance.getEffect());
    }

    /**
     * Clears the float when levitation is removed.
     *
     * @param event the removed effect
     */
    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        clearWhenLevitationEnds(event.getEntity(), event.getEffect());
    }

    private static void clearWhenLevitationEnds(LivingEntity entity, @Nullable Holder<MobEffect> effect) {
        if (MobEffects.LEVITATION.equals(effect) && !entity.level().isClientSide()
                && entity.hasData(GooAttachments.FLOATING)) {
            entity.removeData(GooAttachments.FLOATING);
        }
    }
}
