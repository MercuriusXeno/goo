package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.LeechPayload;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Drained life through Leech goo: damage dealt to a living thing standing
 * in Leech goo heals the one who dealt it by the damage dealt, where that
 * one is a living entity or a player striking directly; damage with no
 * entity source, the goo's own spirits among it, heals nobody. The life
 * splashes off the victim as the hex wisps Lifetap drew, with a soul's escape.
 * damage-dealt-to-a-mob-in-leech-goo-becomes-drained-life-for-its-source
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class LeechDrain {

    private static final double BODY_CENTER = 0.5;
    private static final float DRAIN_VOLUME = 0.9f;
    private static final float DRAIN_PITCH = 0.8f;
    private static final float DRAIN_PITCH_SPREAD = 0.4f;

    private LeechDrain() {
    }

    /**
     * What dealt a hit, as far as drained life asks.
     */
    public enum DrainSource {
        /** No entity dealt it: the spirits, a fall, a fire. */
        NONE,
        /** An entity dealt it through something else, a projectile or a summoned thing. */
        INDIRECT,
        /** A living entity or a player struck it directly. */
        DIRECT_LIVING;

        /**
         * What dealt a damage source's hit.
         *
         * @param source the damage source
         * @return a direct living striker, an indirect one, or none
         */
        static DrainSource of(DamageSource source) {
            if (source.getEntity() == null) {
                return NONE;
            }
            return source.isDirect() && source.getEntity() instanceof LivingEntity ? DIRECT_LIVING : INDIRECT;
        }
    }

    /**
     * The life a hit drains to its source: the damage dealt where a living
     * source struck a victim in Leech goo directly, nothing otherwise.
     *
     * @param damage        the damage the hit dealt
     * @param victimInLeech whether the victim stands in Leech goo
     * @param source        what dealt the hit
     * @return the health its source heals, 0 where it drains none
     */
    static float drainedLife(float damage, boolean victimInLeech, DrainSource source) {
        return victimInLeech && source == DrainSource.DIRECT_LIVING ? Math.max(0f, damage) : 0f;
    }

    /**
     * Whether a living thing stands in Leech goo: a slowing cell fills the
     * block its feet are in, or holds it up.
     *
     * @param living the living thing
     * @return true where Leech goo caught it
     */
    static boolean standsInLeech(LivingEntity living) {
        return VolumeCellBlock.hasSurface(living.level().getBlockState(living.blockPosition()).getBlock(),
                CellSurface.SLOW)
                || VolumeCellBlock.hasSurface(living.level().getBlockState(living.getOnPos()).getBlock(),
                CellSurface.SLOW);
    }

    /**
     * Heals the direct living source of a hit on a victim in Leech goo by
     * the damage the hit dealt.
     *
     * @param event the damage event, after the damage landed
     */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()
                || !(event.getSource().getEntity() instanceof LivingEntity source) || source == victim) {
            return;
        }
        float drained = drainedLife(event.getNewDamage(), standsInLeech(victim), DrainSource.of(event.getSource()));
        if (drained > 0f) {
            source.heal(drained);
            EntityVisuals.sendToWatchers(victim, new LeechPayload(victim.getId(), source.getId(), drained, true));
            victim.level().playSound(null, victim.getX(), victim.getY(BODY_CENTER), victim.getZ(),
                    SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, DRAIN_VOLUME,
                    DRAIN_PITCH + victim.getRandom().nextFloat() * DRAIN_PITCH_SPREAD);
        }
    }
}
