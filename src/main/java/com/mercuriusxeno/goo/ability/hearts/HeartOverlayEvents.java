package com.mercuriusxeno.goo.ability.hearts;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.frost.IcebornEvents;
import com.mercuriusxeno.goo.ability.nether.UndeadEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.Optional;

/**
 * Runs a player's heart overlay against the world: hits drain it before real
 * health, a melee attacker pays for its shields, and each tick ends it at
 * expiry, quenches it in water and regrows its shields (decisions
 * overlay-hearts-are-an-elemental-overshield, aggravated-damage-is-a-per-heart-rule,
 * kindle-ember-hearts-ash-and-retaliate and barkskin-bark-hearts-thorn-and-burn).
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class HeartOverlayEvents {

    /** The reach, in blocks, a strike counts as melee within. */
    private static final double MELEE_REACH = 4.0;
    private static final int RETALIATION_BURN_SECONDS = 3;
    /** A stone heart is worth half a heart against an explosion or a pickaxe: each hit counts double. */
    static final float STONE_BRITTLE_SHARE = 2f;
    /** A physical hit finds a frozen heart worth half a heart (decision iceborn-frozen-hearts-thaw-on-fire). */
    static final float ICE_BRITTLE_SHARE = 2f;

    private HeartOverlayEvents() {
    }

    /**
     * Spares an all-ember bar from fire, and makes a mob that strikes at melee
     * reach pay by the shield count read before the hit breaks one: fire for
     * Kindle's embers, thorns for Barkskin's bark.
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
        float shields = overlay.shieldHearts();
        Mob attacker = meleeAttacker(event.getSource(), player);
        if (shields > 0f && attacker != null) {
            retaliate(overlay.kind(), player, attacker, shields);
        }
    }

    private static void retaliate(HeartKind kind, ServerPlayer player, Mob attacker, float shields) {
        if (kind == HeartKind.KINDLE) {
            // kindle-ember-hearts-ash-and-retaliate: retaliatory fire by the number of ember hearts
            attacker.hurtServer(player.level(), player.damageSources().inFire(), shields);
            attacker.igniteForSeconds(RETALIATION_BURN_SECONDS);
        } else if (kind == HeartKind.BARKSKIN) {
            // barkskin-bark-hearts-thorn-and-burn: thorns equal to the bark heart count
            attacker.hurtServer(player.level(), player.damageSources().thorns(player), shields);
        }
    }

    /**
     * Answers whether a hit is fire landing on a kindled bar that is all ember,
     * which fire cannot hurt.
     *
     * @param source  the damage source
     * @param player  the struck player
     * @param overlay the player's overlay
     * @return true when the hit is fire on an all-ember bar
     */
    private static boolean fireSparesTheBar(DamageSource source, ServerPlayer player, HeartOverlay overlay) {
        // kindle-ember-hearts-ash-and-retaliate: fire hurts only a bar that is not all ember
        return overlay.stands() && overlay.kind() == HeartKind.KINDLE && source.is(DamageTypeTags.IS_FIRE)
                && overlay.allShielded(player.getHealth());
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
     * Runs a hit through the overlay before real health and passes on what
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
        if (thawsIceborn(overlay, event.getSource())) {
            // iceborn-frozen-hearts-thaw-on-fire: fire thaws every frozen heart at once and ends the effect
            IcebornEvents.thaw(player);
            return;
        }
        HeartOverlay.Drained drained = strike(overlay, event.getSource(), event.getNewDamage(), player);
        if (drained.overlay().fireReadyAt() != overlay.fireReadyAt()) {
            // the fire that paid for a relight goes out, so its ticks cannot pay again
            player.clearFire();
        }
        if (drained.overlay() != overlay) {
            player.setData(GooAttachments.HEART_OVERLAY, drained.overlay());
        }
        event.setNewDamage(drained.remainder());
    }

    private static HeartOverlay.Drained strike(HeartOverlay overlay, DamageSource source, float damage,
                                               ServerPlayer player) {
        long now = player.level().getGameTime();
        if (overlay.kind() == HeartKind.KINDLE && source.is(DamageTypeTags.IS_FIRE)) {
            return overlay.burn(damage, player.getHealth(), now);
        }
        if (aggravates(overlay.kind(), source)) {
            return overlay.aggravate(damage, now);
        }
        return brittleStrike(overlay, source, damage, now).orElseGet(() -> overlay.drain(damage, now));
    }

    /**
     * Runs a hit through hearts that a hit can find worth less than a heart:
     * stone against explosions and pickaxes, frozen hearts against a physical hit.
     *
     * @param overlay the player's overlay
     * @param source  the damage source
     * @param damage  the hit's damage
     * @param now     the game time
     * @return the overlay after the hit, or empty for a kind no hit finds brittle
     */
    private static Optional<HeartOverlay.Drained> brittleStrike(HeartOverlay overlay, DamageSource source,
                                                                float damage, long now) {
        if (overlay.kind() == HeartKind.STONESKIN) {
            return Optional.of(overlay.drainScaled(damage, stoneShare(source, overlay.damageTaken()), now));
        }
        if (overlay.kind() == HeartKind.ICEBORN) {
            return Optional.of(overlay.drainScaled(damage, iceShare(source), now));
        }
        return Optional.empty();
    }

    /**
     * Answers whether a hit is one the kind's hearts are especially weak to:
     * fire or an axe on bark, and sunlight on nether.
     *
     * @param kind   the overlay's kind
     * @param source the damage source
     * @return true when the hit is aggravated against the kind
     */
    private static boolean aggravates(HeartKind kind, DamageSource source) {
        // undead-nether-hearts-burn-in-sunlight: sunlight is aggravated against nether hearts
        return kind == HeartKind.BARKSKIN && burnsBark(source)
                || kind == HeartKind.UNDEAD && source.is(UndeadEvents.SUNBURN);
    }

    /**
     * The share of a hit stone hearts take: an explosion or a pickaxe finds a
     * stone heart worth only half a heart, a physical hit, one armor checks,
     * is reduced by the brew's multiplier, and any other hit lands whole.
     *
     * @param source      the damage source
     * @param damageTaken the brew's physical multiplier
     * @return the share of the hit the stone takes
     */
    static float stoneShare(DamageSource source, float damageTaken) {
        ItemStack weapon = source.getWeaponItem();
        // stoneskin-stone-hearts-block-regeneration: half a heart against explosions and pickaxes
        if (source.is(DamageTypeTags.IS_EXPLOSION) || weapon != null && weapon.is(ItemTags.PICKAXES)) {
            return STONE_BRITTLE_SHARE;
        }
        return source.is(DamageTypeTags.BYPASSES_ARMOR) ? HeartOverlay.WHOLE_HIT : damageTaken;
    }

    /**
     * Whether a hit is fire reaching a standing Iceborn overlay, which thaws it whole.
     * iceborn-frozen-hearts-thaw-on-fire
     *
     * @param overlay the player's overlay
     * @param source  the damage source
     * @return true for fire on frozen hearts
     */
    static boolean thawsIceborn(HeartOverlay overlay, DamageSource source) {
        return overlay.kind() == HeartKind.ICEBORN && overlay.stands() && source.is(DamageTypeTags.IS_FIRE);
    }

    /**
     * The share of a hit frozen hearts take: a physical hit, one armor checks,
     * finds a frozen heart worth only half a heart, and any other hit lands whole.
     * iceborn-frozen-hearts-thaw-on-fire
     *
     * @param source the damage source
     * @return the share of the hit the frozen hearts take
     */
    static float iceShare(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_ARMOR) ? HeartOverlay.WHOLE_HIT : ICE_BRITTLE_SHARE;
    }

    /**
     * Answers whether a hit is one bark is especially weak to: fire, or a
     * weapon in the axes tag, since axes carry no damage type of their own.
     *
     * @param source the damage source
     * @return true when the hit is aggravated against bark
     */
    private static boolean burnsBark(DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        // barkskin-bark-hearts-thorn-and-burn: fire and axes deal aggravated damage
        return source.is(DamageTypeTags.IS_FIRE) || weapon != null && weapon.is(ItemTags.AXES);
    }

    /**
     * Holds a stoneskinned player's health from regenerating while stone
     * stands, and brings health a burning kindled player regains back as
     * ember hearts.
     *
     * @param event the heal event, before the heal lands
     */
    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.HEART_OVERLAY)) {
            return;
        }
        if (player.getData(GooAttachments.HEART_OVERLAY).blocksHealing()) {
            // stoneskin-stone-hearts-block-regeneration: no regeneration while stone fills the missing hearts
            event.setCanceled(true);
            return;
        }
        if (!burningWithKindle(player)) {
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

    private static boolean burningWithKindle(ServerPlayer player) {
        return (player.isOnFire() || player.isInLava()) && player.hasData(GooAttachments.HEART_OVERLAY)
                && player.getData(GooAttachments.HEART_OVERLAY).kind() == HeartKind.KINDLE;
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
