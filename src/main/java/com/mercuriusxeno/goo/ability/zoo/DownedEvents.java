package com.mercuriusxeno.goo.ability.zoo;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.pulse.StunEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Lays a rallied mob downed where an attack would kill it, holds attacks and
 * falls off it through its immunity, and kills it once its death span runs
 * out unhealed. The world's harm kills a rallied mob outright, and a mob
 * already down that dies, dies. Downed outlasts the rally: ending the rally
 * leaves the fall and its timers standing.
 * rallied-mob-falls-downed-instead-of-dying
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class DownedEvents {

    /** Half a heart, the health a mob goes down with. */
    static final float DOWNED_HEALTH = 1f;

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
            down(mob);
        }
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
     * Cancels an attack or a fall on a downed mob inside its immunity.
     *
     * @param event the incoming damage event, before the damage lands
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()
                && mob.hasData(GooAttachments.DOWNED)
                && Downed.negates(mob.getData(GooAttachments.DOWNED).ticksDown(mob.level().getGameTime()),
                event.getSource())) {
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
     * time, its target, path and hunting memories dropped, and its AI held
     * off, so it lies still until raised or dead; every mob holding it lets go.
     * nothing-targets-a-downed-mob
     *
     * @param mob the mob going down
     */
    static void down(Mob mob) {
        mob.setHealth(DOWNED_HEALTH);
        mob.setData(GooAttachments.DOWNED, new Downed(mob.level().getGameTime(), mob.isNoAi()));
        StunEvents.forget(mob);
        mob.setNoAi(true);
        if (mob.level() instanceof ServerLevel level) {
            DownedTargets.dropHoldsOn(level, mob);
        }
    }
}
