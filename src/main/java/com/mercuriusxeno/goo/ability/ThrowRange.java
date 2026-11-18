package com.mercuriusxeno.goo.ability;

/**
 * How far an ability aims and lands: an ability thrown or cast at its aim
 * reaches the range its delivery names, Knives and Dart their own reach, and
 * every other ability the glove's unthrown aim.
 * decision each-ability-names-its-range-and-the-shared-throw-range-retires
 */
public final class ThrowRange {

    /** Knives' and Dart's reach, which they name none of, reaching by their flight as projectiles. */
    public static final double KNIVES_AND_DART_REACH = 64;
    /** How far the glove aims for a stream, a self ability, a scan or a rolling arc, none landing at the aim. */
    public static final double UNTHROWN_AIM_RANGE = 64;
    /**
     * How far an ability thrown or cast at its aim reaches where its delivery names no range.
     * decision abilities-leaving-the-shared-range-start-at-sixteen-blocks
     */
    public static final double UNNAMED_THROW_RANGE = 16;

    private ThrowRange() {
    }

    /**
     * How far an ability aims and lands.
     *
     * @param badge    the ability's badge
     * @param delivery the ability's delivery
     * @return the range in blocks
     */
    public static double of(AbilityBadge badge, Delivery delivery) {
        if (!delivery.thrownOrCastAtTheAim()) {
            return UNTHROWN_AIM_RANGE;
        }
        return delivery.throwRange(knivesOrDart(badge, delivery) ? KNIVES_AND_DART_REACH : UNNAMED_THROW_RANGE);
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
