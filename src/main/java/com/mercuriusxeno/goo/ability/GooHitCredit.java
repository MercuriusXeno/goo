package com.mercuriusxeno.goo.ability;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Names the player behind a goo hit on the hit's damage source, keeping the
 * hit's damage type, so vanilla reads the kill, its drops, its death message,
 * its statistics and the struck mob's anger as that player's. The source names
 * the player as its cause and no direct entity, so it carries no position and
 * pushes the struck mob the way the uncredited hit did. Goo's own listeners
 * read a credited hit through {@link #causeOf}, as the uncredited hit it
 * stands in for, so minion sparing, rally, charm, downed and the rest act as
 * they did before goo hits named their player.
 * direct-goo-hit-credits-its-caster
 * goo-hit-keeps-its-kind-and-names-the-player-behind-it
 */
public final class GooHitCredit {

    private GooHitCredit() {
    }

    /**
     * The source a goo hit hurts with: the uncredited source naming the player
     * behind it, or the uncredited source itself where no player stands behind
     * it, the player strikes itself, or the target is that player's own
     * rallied or charmed mob.
     *
     * @param uncredited the source the hit hurts with naming no one
     * @param target     the entity the hit reaches
     * @param cause      the entity behind the hit, or null when unknown
     * @return the source to hurt with
     */
    public static DamageSource credit(DamageSource uncredited, LivingEntity target, @Nullable Entity cause) {
        if (!(cause instanceof Player player) || player == target || OwnMinions.ownMinionOf(player, target)) {
            return uncredited;
        }
        return new CreditedGooHit(uncredited.typeHolder(), player);
    }

    /**
     * The entity a goo listener reads as a hit's attacker: the source's cause,
     * or none for a credited goo hit, which reads as the uncredited hit it
     * stands in for.
     *
     * @param source the hit's source
     * @return the attacker, or null for none
     */
    public static @Nullable Entity causeOf(DamageSource source) {
        return source instanceof CreditedGooHit ? null : source.getEntity();
    }

    /**
     * A goo hit naming the player behind it as its cause and no direct entity.
     */
    private static final class CreditedGooHit extends DamageSource {
        private CreditedGooHit(Holder<DamageType> type, Player player) {
            super(type, null, player);
        }
    }
}
