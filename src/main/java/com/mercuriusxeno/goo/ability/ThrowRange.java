package com.mercuriusxeno.goo.ability;

/**
 * How far an ability aims and lands: the range its data file names on its
 * delivery, 16 blocks where it names none, and Knives' and Dart's own reach
 * for theirs.
 * decision each-ability-names-its-range-and-the-shared-throw-range-retires
 */
public final class ThrowRange {

    /** Knives' and Dart's reach, which they name none of, reaching by their flight as projectiles. */
    public static final double KNIVES_AND_DART_REACH = 64;
    /**
     * How far an ability aims and lands where its delivery names no range.
     * decision abilities-leaving-the-shared-range-start-at-sixteen-blocks
     */
    public static final double UNNAMED_RANGE = 16;

    private ThrowRange() {
    }

    /**
     * How far an ability aims and lands: a throw's or a cast's range, a
     * stream's reach, a scan's read, a rolling arc's roll, or a self
     * ability's aim, each as its delivery names it.
     *
     * @param badge    the ability's badge
     * @param delivery the ability's delivery
     * @return the range in blocks
     */
    public static double of(AbilityBadge badge, Delivery delivery) {
        if (delivery.range() > 0) {
            return delivery.range();
        }
        return knivesOrDart(badge, delivery) ? KNIVES_AND_DART_REACH : UNNAMED_RANGE;
    }

    /**
     * How far an ability aims and lands.
     *
     * @param ability the ability
     * @return the range in blocks
     */
    public static double of(AbilityDefinition ability) {
        return of(ability.badge(), ability.delivery());
    }

    // A held arc is Knives' fan or Dart's javelin.
    private static boolean knivesOrDart(AbilityBadge badge, Delivery delivery) {
        return badge == AbilityBadge.HELD && delivery.kind() == DeliveryKind.ARC;
    }
}
