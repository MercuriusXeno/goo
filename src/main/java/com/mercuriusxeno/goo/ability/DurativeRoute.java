package com.mercuriusxeno.goo.ability;

import org.jspecify.annotations.Nullable;

/**
 * The route a durative ability takes: one wearing the durative badge on a
 * self delivery charges while right click is held and, on release, holds its
 * effect, which trickles until ended; invoking one already held ends it
 * instead. A self ability wearing the self badge runs on command. The route
 * is decided by badge and delivery kind alone, never by ability name, so an
 * ability added later wearing the durative badge on a self delivery takes it
 * with no further change. Every read here takes no level, so a unit test
 * reaches it whole.
 * decision self-effects-trickle-until-ended
 * decision durative-self-abilities-wash-over-the-view-in-place-of-eating
 */
public final class DurativeRoute {

    private DurativeRoute() {
    }

    /**
     * Whether an ability is durative.
     *
     * @param delivery the ability's delivery, or null where the glove holds no selection
     * @param badge    the ability's badge, or null where the glove holds no selection
     * @return true for the durative badge on a self delivery
     */
    public static boolean isDurative(@Nullable Delivery delivery, @Nullable AbilityBadge badge) {
        return delivery != null && delivery.kind() == DeliveryKind.SELF && badge == AbilityBadge.DURATIVE;
    }

    /**
     * Whether an invoke ends a held effect rather than holding it: a durative
     * ability that the player already holds.
     * self-effects-trickle-until-ended
     *
     * @param delivery the ability's delivery, or null where the glove holds no selection
     * @param badge    the ability's badge, or null where the glove holds no selection
     * @param held     whether the player holds the ability
     * @return true when the invoke ends the held effect
     */
    public static boolean endsHeld(@Nullable Delivery delivery, @Nullable AbilityBadge badge, boolean held) {
        return held && isDurative(delivery, badge);
    }
}
