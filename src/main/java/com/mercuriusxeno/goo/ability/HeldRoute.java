package com.mercuriusxeno.goo.ability;

import org.jspecify.annotations.Nullable;

/**
 * The held route an ability takes: it runs on every tick the glove's use
 * stays down rather than once on release. A stream sprays its cone each
 * held tick (decision stream-delivery-held-cone), and a self delivery
 * wearing the channeled badge runs on the player each held tick, carrying
 * the hold's aim (decision flatten-disc-cursor-breaks-above-the-plane).
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
     * @return true for a stream, or a self delivery wearing the channeled badge
     */
    public static boolean runsWhileHeld(@Nullable Delivery delivery, @Nullable AbilityBadge badge) {
        return delivery != null && (delivery.kind() == DeliveryKind.STREAM || channelsOnSelf(delivery, badge));
    }

    /**
     * Whether an ability is a channel run on the player each held tick.
     *
     * @param delivery the ability's delivery, or null where none is selected
     * @param badge    the ability's badge, or null where none is selected
     * @return true for a self delivery wearing the channeled badge that does not charge; a charged
     *         channel fires once on release (decision nova-ring-grows-with-the-hold)
     */
    public static boolean channelsOnSelf(@Nullable Delivery delivery, @Nullable AbilityBadge badge) {
        return delivery != null && delivery.kind() == DeliveryKind.SELF && badge == AbilityBadge.CHANNELED
                && !delivery.charges();
    }
}
