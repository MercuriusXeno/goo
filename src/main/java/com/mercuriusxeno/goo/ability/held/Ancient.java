package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.network.AilmentPayload;
import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Yore's Ancient on a player: while the held effect stands, no damage takes
 * the player below half a heart, and the aged stone-and-gold overlay stands
 * on the player's model for every watcher, cleared when it ends.
 * ancient-makes-the-player-immortal
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class Ancient {

    /** Half a heart, the least health Ancient leaves standing. */
    static final float HALF_HEART = 1f;
    /** Ticks between one overlay refresh and the next, so a player who starts watching sees it within a second. */
    static final int OVERLAY_REFRESH_TICKS = 20;
    /** How long each refresh lasts: past the next refresh and its fade, so the overlay never dips. */
    static final int OVERLAY_TICKS = OVERLAY_REFRESH_TICKS * 3;
    private static final int OVERLAY_CLEARED = 0;
    private static final float NO_DAMAGE = 0f;

    private Ancient() {
    }

    /**
     * Starts Ancient's overlay on a target.
     *
     * @param target the target
     */
    public static void lay(LivingEntity target) {
        sendOverlay(target, OVERLAY_TICKS);
    }

    /**
     * Clears Ancient's overlay from a player as its held effect ends.
     *
     * @param player the player
     */
    static void clear(ServerPlayer player) {
        sendOverlay(player, OVERLAY_CLEARED);
    }

    /**
     * Whether the player's held effects lay Ancient.
     *
     * @param player the player
     * @return true while Ancient stands
     */
    public static boolean standsOn(ServerPlayer player) {
        return player.hasData(GooAttachments.HELD_EFFECTS) && player.getData(GooAttachments.HELD_EFFECTS).held()
                .stream().anyMatch(held -> held.lays().contains(LaidState.ANCIENT));
    }

    /**
     * The damage an Ancient player takes: as much of the hit as leaves half
     * a heart of health and absorption standing.
     *
     * @param health     the player's health
     * @param absorption the player's absorption
     * @param damage     the hit's damage before absorption
     * @return the damage the player takes
     */
    static float survivableDamage(float health, float absorption, float damage) {
        return Math.max(NO_DAMAGE, Math.min(damage, health + absorption - HALF_HEART));
    }

    /**
     * Caps each hit on an Ancient player at what leaves half a heart, after
     * every other handler has had its say.
     *
     * @param event the damage event, after armor and before absorption
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && standsOn(player)) {
            event.setNewDamage(survivableDamage(player.getHealth(), player.getAbsorptionAmount(),
                    event.getNewDamage()));
        }
    }

    /**
     * Holds an Ancient player at half a heart against a death no hit's
     * damage caused, such as a kill that sets health outright.
     *
     * @param event the death event
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && standsOn(player)) {
            event.setCanceled(true);
            player.setHealth(HALF_HEART);
        }
    }

    /**
     * Refreshes the overlay of each player whose held effects lay Ancient.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % OVERLAY_REFRESH_TICKS == 0
                && standsOn(player)) {
            sendOverlay(player, OVERLAY_TICKS);
        }
    }

    private static void sendOverlay(LivingEntity target, int ticks) {
        EntityVisuals.sendToWatchers(target, new AilmentPayload(target.getId(), AilmentKind.ANCIENT, ticks));
    }
}
