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
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
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
        if (fireSparesTheBar(event.getSource(), player, overlay)) {
            event.setCanceled(true);
            player.clearFire();
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
     * Answers whether a hit is fire landing on a bar that is all ember, which
     * fire cannot hurt.
     *
     * @param source  the damage source
     * @param player  the struck player
     * @param overlay the player's overlay
     * @return true when the hit is fire on an all-ember bar
     */
    private static boolean fireSparesTheBar(DamageSource source, ServerPlayer player, HeartOverlay overlay) {
        // kindle-ember-hearts-ash-and-retaliate: fire hurts only a bar that is not all ember
        return overlay.stands() && source.is(DamageTypeTags.IS_FIRE) && overlay.allEmber(player.getHealth());
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
        long now = player.level().getGameTime();
        HeartOverlay.Drained drained = event.getSource().is(DamageTypeTags.IS_FIRE)
                ? overlay.burn(event.getNewDamage(), player.getHealth(), now)
                : overlay.drain(event.getNewDamage(), now);
        if (drained.overlay().igniteReadyAt() != overlay.igniteReadyAt()) {
            // the fire that paid for a relight goes out, so its ticks cannot pay again
            player.clearFire();
        }
        if (drained.overlay() != overlay) {
            player.setData(GooAttachments.HEART_OVERLAY, drained.overlay());
        }
        event.setNewDamage(drained.remainder());
    }

    /**
     * Brings health a burning player regains back as ember hearts.
     *
     * @param event the heal event, before the heal lands
     */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.HEART_OVERLAY)
                || !(player.isOnFire() || player.isInLava())) {
            return;
        }
        HeartOverlay overlay = player.getData(GooAttachments.HEART_OVERLAY);
        float health = player.getHealth();
        // kindle-ember-hearts-ash-and-retaliate: hearts regained in fire come back ignited
        HeartOverlay lit = overlay.healInFire(health, Math.min(player.getMaxHealth(), health + event.getAmount()));
        if (lit != overlay) {
            player.setData(GooAttachments.HEART_OVERLAY, lit);
        }
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
