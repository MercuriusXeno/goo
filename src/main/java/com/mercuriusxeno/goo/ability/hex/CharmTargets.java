package com.mercuriusxeno.goo.ability.hex;

import org.jspecify.annotations.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Whom a charmed mob fights: the nearest living mob targeting its charmer,
 * else the nearest living monster hostile to players before it aggresses,
 * neutral mobs spared until one targets the charmer.
 * charm-glisten-and-icon-over-the-head
 */
public final class CharmTargets {

    private CharmTargets() {
    }

    /**
     * One mob near a charmed mob, as the pick weighs it.
     *
     * @param mob         the mob
     * @param alive       whether it still lives
     * @param hostile     whether it is a monster hostile to players unprovoked, neither neutral nor charmed
     * @param target      the UUID of the entity it targets, or null for none
     * @param distanceSqr its squared distance from the charmed mob
     * @param <T>         the mob's type
     */
    public record Candidate<T>(T mob, boolean alive, boolean hostile, @Nullable UUID target, double distanceSqr) {
    }

    /**
     * Picks the foe a charmed mob turns on: of the living mobs near it, other
     * than itself, the nearest one targeting the charmer, else the nearest
     * hostile one.
     *
     * @param charmer the player the charmed mob fights for
     * @param nearby  the mobs near it, itself left out
     * @param <T>     the mob's type
     * @return the foe, or empty when no mob near it is a foe
     */
    public static <T> Optional<T> nearestFoe(UUID charmer, List<Candidate<T>> nearby) {
        return nearest(nearby, candidate -> charmer.equals(candidate.target()))
                .or(() -> nearest(nearby, Candidate::hostile));
    }

    private static <T> Optional<T> nearest(List<Candidate<T>> nearby, Predicate<Candidate<T>> foe) {
        return nearby.stream()
                .filter(candidate -> candidate.alive() && foe.test(candidate))
                .min(Comparator.comparingDouble(Candidate::distanceSqr))
                .map(Candidate::mob);
    }
}
