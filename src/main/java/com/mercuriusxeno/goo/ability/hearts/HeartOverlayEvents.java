package com.mercuriusxeno.goo.ability.hearts;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Runs a player's heart overlay against the world: hits drain it before real
 * health, a melee attacker burns on its embers, and each tick ends it at
 * expiry, quenches it in water and reignites its ash (decisions
 * overlay-hearts-are-an-elemental-overshield and
 * kindle-ember-hearts-ash-and-retaliate).
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class HeartOverlayEvents {

    /** The reach, in blocks, a strike counts as melee within. */
    private static final double MELEE_REACH = 4.0;
    private static final int RETALIATION_BURN_SECONDS = 3;

    private HeartOverlayEvents() {
    }

    /**
     * Burns a mob that strikes a kindled player at melee reach for fire damage
     * by the ember count, read before the hit breaks one.
     *
     * @param event the incoming damage event
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.HEART_OVERLAY)) {
            return;
        }
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        if (overlay.stands() && event.getSource().is(DamageTypeTags.IS_FIRE)) {
            igniteInstead(event, player, overlay);
            return;
        }
        int embers = overlay.emberCount();
        Mob attacker = meleeAttacker(event.getSource(), player);
        if (embers > 0 && attacker != null) {
            // kindle-ember-hearts-ash-and-retaliate: retaliatory fire by the number of ember hearts
            attacker.hurtServer(player.level(), player.damageSources().inFire(), embers);
            attacker.igniteForSeconds(RETALIATION_BURN_SECONDS);
        }
    }

    /**
     * Cancels a fire hit on an overlay, puts the player's own burning out so
     * its ticks cannot keep relighting the bar, and reignites the overlay's
     * ash when fire's cooldown allows.
     *
     * @param event   the incoming fire damage
     * @param player  the struck player
     * @param overlay the player's standing overlay
     */
    private static void igniteInstead(LivingIncomingDamageEvent event, ServerPlayer player, HeartOverlay overlay) {
        event.setCanceled(true);
        player.clearFire();
        HeartOverlay ignited = overlay.ignite(player.getHealth(), player.level().getGameTime());
        if (ignited != overlay) {
            player.setData(GooAttachments.HEART_OVERLAY, ignited);
        }
    }

    /**
     * The mob that struck with its own body at melee reach, or null for a
     * projectile, a far strike or a source that is no mob.
     *
     * @param source the damage source
     * @param player the struck player
     * @return the striking mob, or null
     */
    private static Mob meleeAttacker(DamageSource source, ServerPlayer player) {
        if (source.getDirectEntity() instanceof Mob attacker && source.getEntity() == attacker
                && attacker.distanceTo(player) <= MELEE_REACH) {
            return attacker;
        }
        return null;
    }

    /**
     * Drains a hit through the overlay before real health and passes on what
     * gets through.
     *
     * @param event the damage event, after armor and before absorption
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.HEART_OVERLAY)) {
            return;
        }
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        HeartOverlay.Drained drained = overlay.drain(event.getNewDamage(), player.level().getGameTime());
        if (drained.overlay() != overlay) {
            player.setData(GooAttachments.HEART_OVERLAY, drained.overlay());
        }
        event.setNewDamage(drained.remainder());
    }

    /**
     * Advances a standing overlay one tick on the server.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.HEART_OVERLAY)) {
            return;
        }
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        ServerLevel level = player.level();
        boolean wet = player.isInWaterOrRain() || level.getBlockState(player.getOnPos()).is(BlockTags.ICE);
        HeartOverlay after = overlay.tick(player.getHealth(), wet, level.getGameTime());
        if (after != overlay) {
            player.setData(GooAttachments.HEART_OVERLAY, after);
        }
    }
}
