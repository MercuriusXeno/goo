package com.mercuriusxeno.goo.ability.zoo;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.pulse.StunEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lays a rallied mob downed where an attack would kill it, carrying the
 * negative health the blow left it, holds attacks and falls off it for as
 * long as it is down, and kills it once its death span runs out unhealed.
 * The world's harm kills a rallied mob outright, and kills a mob already
 * down. Downed outlasts the rally: ending the rally leaves the fall and its
 * timers standing.
 * rallied-mob-falls-downed-instead-of-dying
 * no-attack-lands-on-a-downed-mob
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class DownedEvents {

    /**
     * Half a heart, the vanilla health a downed mob holds while its count
     * lies at or below 0, so no vanilla death fires on a mob lying down.
     */
    static final float DOWNED_HEALTH = 1f;

    /** The health the last blow left each rallied mob standing, read by the death it deals. */
    private static final Map<Mob, Float> HEALTH_LEFT_BY_THE_BLOW = new WeakHashMap<>();

    private DownedEvents() {
    }

    /**
     * Cancels the death an attack deals a rallied mob standing, and lays it
     * downed instead; first, so no other death handler acts on a mob that lives.
     *
     * @param event the death event
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob && standsRallied(mob) && Downed.downs(event.getSource())) {
            event.setCanceled(true);
            down(mob, healthLeftBy(HEALTH_LEFT_BY_THE_BLOW.remove(mob)));
        }
    }

    /**
     * Records the health a blow leaves a rallied mob standing, below zero
     * where it is lethal: the health it had less the damage that lands once
     * absorption takes its share, as vanilla takes it right after this event.
     * Last, so every other handler has set the damage. The death the blow
     * deals reads it back, since the death event carries no damage.
     * downed-mob-revives-only-healed-past-zero
     *
     * @param event the damage event, before absorption and the health loss
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof Mob mob && standsRallied(mob)) {
            float damage = event.getNewDamage();
            float landed = damage - Math.min(mob.getAbsorptionAmount(), damage);
            HEALTH_LEFT_BY_THE_BLOW.put(mob, mob.getHealth() - landed);
        }
    }

    /**
     * The negative health a mob goes down with: what its killing blow left
     * it, or 0 for a death no recorded blow dealt.
     *
     * @param healthLeft the health the last blow left, or null for none
     * @return the negative health, at or below 0
     */
    private static float healthLeftBy(@Nullable Float healthLeft) {
        return healthLeft == null ? 0f : Math.min(healthLeft, 0f);
    }

    /**
     * Whether a mob on the server holds a rally and has not gone down yet.
     *
     * @param mob the mob
     * @return true for a rallied mob still standing
     */
    private static boolean standsRallied(Mob mob) {
        return !mob.level().isClientSide() && mob.hasData(GooAttachments.RALLIED)
                && !mob.hasData(GooAttachments.DOWNED);
    }

    /**
     * Cancels an attack or a fall on a downed mob, at any time down, so
     * neither moves its negative health.
     * no-attack-lands-on-a-downed-mob
     *
     * @param event the incoming damage event, before the damage lands
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()
                && mob.hasData(GooAttachments.DOWNED) && Downed.negates(event.getSource())) {
            event.setCanceled(true);
        }
    }

    /**
     * Kills a mob still downed once its death span has run out.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Mob mob && mob.level() instanceof ServerLevel level
                && outlivedItsFall(mob, level)) {
            mob.kill(level);
        }
    }

    /**
     * Whether a living mob lies downed past its death span.
     *
     * @param mob   the mob
     * @param level its level
     * @return true where the mob is due to die
     */
    private static boolean outlivedItsFall(Mob mob, ServerLevel level) {
        return mob.isAlive() && mob.hasData(GooAttachments.DOWNED)
                && mob.getData(GooAttachments.DOWNED).diesAt(level.getGameTime());
    }

    /**
     * Lays a mob downed at half a heart: the fall recorded with the game
     * time and the negative health its killing blow left, its target, path
     * and hunting memories dropped, and its AI held off, so it lies still
     * until raised or dead; every mob holding it lets go.
     * nothing-targets-a-downed-mob
     * downed-mob-revives-only-healed-past-zero
     *
     * @param mob            the mob going down
     * @param negativeHealth the health below zero the killing blow left it
     */
    static void down(Mob mob, float negativeHealth) {
        mob.setHealth(DOWNED_HEALTH);
        mob.setData(GooAttachments.DOWNED, new Downed(mob.level().getGameTime(), mob.isNoAi(), negativeHealth, 0L));
        StunEvents.forget(mob);
        mob.setNoAi(true);
        if (mob.level() instanceof ServerLevel level) {
            DownedTargets.dropHoldsOn(level, mob);
        }
    }
}
