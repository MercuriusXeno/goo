package com.mercuriusxeno.goo.ability.zoo;

import org.jspecify.annotations.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Whom a rallied mob fights: the caster's enemies, the player the rally
 * records as the caster's attacker first, else the living mob that last hurt
 * the caster, else the nearest living mob targeting the caster, else the
 * living mob the caster last struck, else the nearest hostile mob near the
 * caster.
 * zoo-rally-arms-the-peaceful
 * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
 */
public final class RallyFoes {

    private RallyFoes() {
    }

    /**
     * One mob near a rallied mob, as the pick weighs it.
     *
     * @param mob         the mob
     * @param id          its UUID
     * @param alive       whether it still lives
     * @param hostile     whether it is a monster hostile to players before it aggresses
     * @param target      the UUID of the entity it targets, or null for none
     * @param distanceSqr its squared distance from the rallied mob
     * @param <T>         the mob's type
     */
    public record Candidate<T>(T mob, UUID id, boolean alive, boolean hostile, @Nullable UUID target,
                               double distanceSqr) {
    }

    /**
     * Picks the foe a rallied mob turns on.
     *
     * @param caster      the caster the rallied mob fights for
     * @param attacker    the UUID of the player the rally records as the caster's attacker, or null for none
     * @param hurtCaster  the UUID of the mob that last hurt the caster, or null for none
     * @param casterStruck the UUID of the mob the caster last struck, or null for none
     * @param nearby      the mobs near the rallied mob, itself left out
     * @param <T>         the mob's type
     * @return the foe, or empty when no mob near it is the caster's enemy
     */
    public static <T> Optional<T> pick(UUID caster, @Nullable UUID attacker, @Nullable UUID hurtCaster,
                                       @Nullable UUID casterStruck, List<Candidate<T>> nearby) {
        return nearest(nearby, candidate -> candidate.id().equals(attacker))
                .or(() -> nearest(nearby, candidate -> candidate.id().equals(hurtCaster)))
                .or(() -> nearest(nearby, candidate -> caster.equals(candidate.target())))
                .or(() -> nearest(nearby, candidate -> candidate.id().equals(casterStruck)))
                // rallied-mobs-follow-the-player-who-rallied-them: a hostile near the rallier is a foe before it aggroes
                .or(() -> nearest(nearby, Candidate::hostile));
    }

    /**
     * Answers whether the caster's recorded attacker still stands as a foe:
     * alive among the mobs in the rallied mob's reach.
     * rallied-mobs-turn-on-a-player-who-attacks-their-rallier
     *
     * @param attacker the UUID of the player who attacked the caster
     * @param nearby   the mobs near the rallied mob
     * @param <T>      the mob's type
     * @return true while the attacker is alive in reach
     */
    public static <T> boolean attackerStands(UUID attacker, List<Candidate<T>> nearby) {
        return nearby.stream().anyMatch(candidate -> candidate.alive() && candidate.id().equals(attacker));
    }

    private static <T> Optional<T> nearest(List<Candidate<T>> nearby, Predicate<Candidate<T>> foe) {
        return nearby.stream()
                .filter(candidate -> candidate.alive() && foe.test(candidate))
                .min(Comparator.comparingDouble(Candidate::distanceSqr))
                .map(Candidate::mob);
    }
}
