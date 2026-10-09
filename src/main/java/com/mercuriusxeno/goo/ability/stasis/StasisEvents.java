package com.mercuriusxeno.goo.ability.stasis;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Holds a mob in stasis until it is struck: the mob stands still with no AI,
 * immune to all damage and wearing the golden stasis shimmer, with no expiry,
 * and the first strike from an attacker frees it and clears the shimmer.
 * stasis-holds-mob-with-golden-shimmer
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class StasisEvents {

    /**
     * Ticks between one shimmer refresh and the next, so a player who starts
     * watching the mob sees the shimmer within a second.
     */
    static final int SHIMMER_REFRESH_TICKS = 20;
    /** How long each refresh lasts: past the next refresh and its fade, so the shimmer never dips. */
    static final int SHIMMER_DURATION_TICKS = SHIMMER_REFRESH_TICKS * 3;
    /** A landing of no duration, which ends the shimmer on every client. */
    private static final int SHIMMER_CLEARED = 0;

    private StasisEvents() {
    }

    /**
     * Puts a mob in stasis: it loses its AI and starts wearing the shimmer.
     *
     * @param mob the mob the stasis blob landed on
     */
    public static void hold(Mob mob) {
        mob.setData(GooAttachments.STASIS, true);
        mob.setNoAi(true);
        sendShimmer(mob, SHIMMER_DURATION_TICKS);
    }

    /**
     * Answers whether a mob stands in stasis.
     *
     * @param entity the entity
     * @return true while a stasis holds it
     */
    public static boolean held(Entity entity) {
        return entity instanceof Mob && entity.hasData(GooAttachments.STASIS);
    }

    /**
     * Keeps each mob in stasis without AI, as a reload or another effect may
     * have handed it back, and refreshes its shimmer for its watchers.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !held(entity)) {
            return;
        }
        Mob mob = (Mob) entity;
        mob.setNoAi(true);
        if (mob.tickCount % SHIMMER_REFRESH_TICKS == 0) {
            sendShimmer(mob, SHIMMER_DURATION_TICKS);
        }
    }

    /**
     * Keeps a mob in stasis immune to every damage, and frees it when an
     * attacker strikes it: its AI returns and its shimmer clears, the strike
     * itself dealing nothing. Sunlight, falls and other harm with no attacker
     * leave it frozen.
     *
     * @param event the incoming damage event
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide() || !held(entity)) {
            return;
        }
        event.setCanceled(true);
        if (struckByAttacker(event.getSource())) {
            release((Mob) entity);
        }
    }

    /**
     * Answers whether a damage source is an attacker's strike, which frees a
     * mob in stasis.
     *
     * @param source the damage source
     * @return true when an entity caused or dealt the damage
     */
    static boolean struckByAttacker(DamageSource source) {
        return source.getEntity() != null || source.getDirectEntity() != null;
    }

    private static void release(Mob mob) {
        mob.removeData(GooAttachments.STASIS);
        mob.setNoAi(false);
        sendShimmer(mob, SHIMMER_CLEARED);
    }

    private static void sendShimmer(Mob mob, int durationTicks) {
        EntityVisuals.sendToWatchers(mob, new AilmentPayload(mob.getId(), AilmentKind.STASIS, durationTicks));
    }
}
