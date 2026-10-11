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
import net.neoforged.neoforge.event.entity.living.MobDespawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

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
    /**
     * Ticks after a stasis lands during which a strike frees nothing: the
     * glove's punch that lands the blob arrives on the same tick as the stasis.
     */
    static final int LANDING_GRACE_TICKS = 5;
    /** The game time each mob's stasis landed, for the landing's grace; lost on a restart, as the grace is long past. */
    private static final Map<Mob, Long> LANDED_AT = new WeakHashMap<>();

    private StasisEvents() {
    }

    /**
     * Puts a mob in stasis: it loses its AI and starts wearing the shimmer.
     *
     * @param mob the mob the stasis blob landed on
     */
    public static void hold(Mob mob) {
        mob.setData(GooAttachments.STASIS, true);
        LANDED_AT.put(mob, mob.level().getGameTime());
        mob.setNoAi(true);
        sendShimmer(mob, SHIMMER_DURATION_TICKS);
    }

    /**
     * Answers whether a mob stands in stasis.
     *
     * @param entity the entity
     * @return true while a stasis holds it
     */
    public static boolean held(@Nullable Entity entity) {
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
        Mob mob = (Mob) entity;
        if (struckByAttacker(event.getSource()) && pastLandingGrace(LANDED_AT.get(mob), mob.level().getGameTime())) {
            release(mob);
        }
    }

    /**
     * Answers whether a strike comes late enough after the stasis landed to
     * free the mob; the punch that lands the blob does not.
     *
     * @param landedAt the game time the stasis landed, null when unknown
     * @param now      the game time of the strike
     * @return true when the strike frees the mob
     */
    static boolean pastLandingGrace(@Nullable Long landedAt, long now) {
        return landedAt == null || now - landedAt > LANDING_GRACE_TICKS;
    }

    /**
     * A mob in stasis deals no damage: a frozen slime's touch and any other
     * harm it would cause are cancelled.
     *
     * @param event the incoming damage event on the harmed entity
     */
    @SubscribeEvent
    public static void onDamageByFrozen(LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide() && dealtByFrozen(event.getSource())) {
            event.setCanceled(true);
        }
    }

    private static boolean dealtByFrozen(DamageSource source) {
        return held(source.getEntity()) || held(source.getDirectEntity());
    }

    /**
     * Keeps a mob in stasis from despawning, so a frozen hostile stands until
     * it is struck, however far its freezer walks.
     *
     * @param event the despawn check
     */
    @SubscribeEvent
    public static void onDespawnCheck(MobDespawnEvent event) {
        if (held(event.getEntity())) {
            event.setResult(MobDespawnEvent.Result.DENY);
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
        LANDED_AT.remove(mob);
        mob.setNoAi(false);
        sendShimmer(mob, SHIMMER_CLEARED);
    }

    private static void sendShimmer(Mob mob, int durationTicks) {
        EntityVisuals.sendToWatchers(mob, new AilmentPayload(mob.getId(), AilmentKind.STASIS, durationTicks));
    }
}
