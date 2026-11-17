package com.mercuriusxeno.goo.ability;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Names the player behind a goo hit on the hit's damage source, keeping the
 * hit's damage type, so vanilla reads the kill, its drops, its death message,
 * its statistics and the struck mob's anger as that player's. The source names
 * the player as its cause and keeps the uncredited hit's direct entity, a
 * thrown hunk's or none, so it pushes the struck mob from where the uncredited
 * hit did. Goo's own listeners read a credited hit through {@link #causeOf},
 * as the uncredited hit it stands in for, so minion sparing, rally, charm,
 * downed and the rest act as they did before goo hits named their player.
 * direct-goo-hit-credits-its-caster
 * goo-hit-keeps-its-kind-and-names-the-player-behind-it
 */
public final class GooHitCredit {

    private GooHitCredit() {
    }

    /**
     * The source a goo hit hurts with: the uncredited source naming the player
     * behind it, or the uncredited source itself where no player stands behind
     * it, the player strikes itself, the target is that player's own rallied
     * or charmed mob, or the uncredited source names a position of its own,
     * which a credited source cannot carry.
     *
     * @param uncredited the source the hit hurts with naming no one
     * @param target     the entity the hit reaches
     * @param cause      the entity behind the hit, or null when unknown
     * @return the source to hurt with
     */
    public static DamageSource credit(DamageSource uncredited, LivingEntity target, @Nullable Entity cause) {
        if (!(cause instanceof Player player) || player == target || OwnMinions.ownMinionOf(player, target)
                || uncredited.sourcePositionRaw() != null) {
            return uncredited;
        }
        return new CreditedGooHit(uncredited, player);
    }

    /**
     * The player a standing goo thing credits: whoever placed it, while that
     * player is on the server.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @param level  the level the thing stands in
     * @param placer the player who placed it, or null where no player did
     * @return the player, or null where no player placed it or that player is not on the server
     */
    public static @Nullable ServerPlayer placerOf(ServerLevel level, @Nullable UUID placer) {
        return placer == null ? null : level.getServer().getPlayerList().getPlayer(placer);
    }

    /**
     * The entity a goo listener reads as a hit's attacker: the source's cause,
     * or for a credited goo hit the cause of the uncredited hit it stands in
     * for.
     *
     * @param source the hit's source
     * @return the attacker, or null for none
     */
    public static @Nullable Entity causeOf(DamageSource source) {
        return source instanceof CreditedGooHit credited ? credited.uncreditedCause : source.getEntity();
    }

    /**
     * A goo hit naming the player behind it as its cause, keeping the
     * uncredited hit's type, its direct entity and, for goo's listeners, its cause.
     */
    private static final class CreditedGooHit extends DamageSource {
        private final @Nullable Entity uncreditedCause;

        private CreditedGooHit(DamageSource uncredited, Player player) {
            super(uncredited.typeHolder(), uncredited.getDirectEntity(), player);
            this.uncreditedCause = uncredited.getEntity();
        }
    }
}
