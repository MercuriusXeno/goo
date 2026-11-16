package com.mercuriusxeno.goo.ability;

import org.jspecify.annotations.Nullable;

/**
 * The held route an ability takes: it runs on every tick the glove's use
 * stays down rather than once on release. A stream sprays its cone each
 * held tick (decision stream-delivery-held-cone), and a self delivery
 * wearing the channeled or held badge runs on the player each held tick, carrying
 * the hold's aim (decisions flatten-disc-cursor-breaks-above-the-plane,
 * held-badge-marks-abilities-that-prepare-and-throw).
 * The route is decided by delivery kind and badge alone, so the client's
 * press and the server's stream handler read one rule.
 */
public final class HeldRoute {

    private HeldRoute() {
    }

    /**
     * Whether an ability runs while held.
     *
     * @param delivery the ability's delivery, or null where none is selected
     * @param badge    the ability's badge, or null where none is selected
     * @return true for a stream, or a self delivery wearing the channeled or held badge
     */
    public static boolean runsWhileHeld(@Nullable Delivery delivery, @Nullable AbilityBadge badge) {
        return delivery != null && (delivery.kind() == DeliveryKind.STREAM || channelsOnSelf(delivery, badge));
    }

    /**
     * Whether an ability forms a javelin while the glove's use is held and
     * throws it on the attack key, sending nothing as the use comes up: an
     * arc wearing the held badge.
     * decision dart-forms-on-right-click-and-throws-on-left-click
     *
     * @param delivery the ability's delivery, or null where none is selected
     * @param badge    the ability's badge, or null where none is selected
     * @return true for a held arc
     */
    public static boolean throwsOnAttack(@Nullable Delivery delivery, @Nullable AbilityBadge badge) {
        return delivery != null && badge == AbilityBadge.HELD && delivery.kind() == DeliveryKind.ARC;
    }

    /**
     * Whether an ability is a channel run on the player each held tick.
     *
     * @param delivery the ability's delivery, or null where none is selected
     * @param badge    the ability's badge, or null where none is selected
     * @return true for a self delivery wearing the channeled or held badge that does not charge; a
     *         charged channel fires once on release (decision nova-ring-grows-with-the-hold)
     */
    public static boolean channelsOnSelf(@Nullable Delivery delivery, @Nullable AbilityBadge badge) {
        // held-badge-marks-abilities-that-prepare-and-throw
        boolean runsOnEachTick = badge == AbilityBadge.CHANNELED || badge == AbilityBadge.HELD;
        return delivery != null && delivery.kind() == DeliveryKind.SELF && runsOnEachTick && !delivery.charges();
    }
}
