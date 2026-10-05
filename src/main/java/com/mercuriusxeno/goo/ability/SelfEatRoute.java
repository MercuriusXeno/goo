package com.mercuriusxeno.goo.ability;

import net.minecraft.world.item.ItemUseAnimation;
import org.jspecify.annotations.Nullable;

/**
 * The eat route a self delivery takes: invoking a self ability starts the
 * player eating the glove, and the cost drains and the program runs when
 * the eat finishes, so letting go or switching items before then runs
 * nothing and drains nothing. The route is decided by delivery kind alone,
 * never by ability name, so an ability added later with a self delivery
 * eats with no further change. Every read here takes a delivery and no
 * level, so a unit test reaches it whole.
 * decision self-brew-goos-eat-before-the-effect
 */
public final class SelfEatRoute {

    /** Vanilla's eat, 1.6 seconds of ticks (Consumable.DEFAULT_CONSUME_SECONDS). */
    public static final int EAT_TICKS = 32;
    /** The use duration of a glove whose selection takes no eat route. */
    public static final int NO_USE = 0;

    private SelfEatRoute() {
    }

    /**
     * Whether a delivery takes the eat route.
     *
     * @param delivery the selected ability's delivery, or null where the glove holds no selection
     * @return true for a self delivery
     */
    public static boolean eats(@Nullable Delivery delivery) {
        return delivery != null && delivery.kind() == DeliveryKind.SELF;
    }

    /**
     * The use animation the glove reports for a selection.
     *
     * @param delivery the selected ability's delivery, or null where the glove holds no selection
     * @return the eat for a self delivery, none for every other
     */
    public static ItemUseAnimation animation(@Nullable Delivery delivery) {
        return eats(delivery) ? ItemUseAnimation.EAT : ItemUseAnimation.NONE;
    }

    /**
     * The use duration the glove reports for a selection.
     *
     * @param delivery the selected ability's delivery, or null where the glove holds no selection
     * @return the vanilla eat duration for a self delivery, zero for every other
     */
    public static int useDuration(@Nullable Delivery delivery) {
        return eats(delivery) ? EAT_TICKS : NO_USE;
    }

    /**
     * Runs the invoke when an eat finishes under a self delivery; a finish
     * under any other delivery runs nothing.
     *
     * @param delivery the selected ability's delivery, or null where the glove holds no selection
     * @param invoke   the drain and the program run
     */
    public static void finish(@Nullable Delivery delivery, Runnable invoke) {
        if (eats(delivery)) {
            invoke.run();
        }
    }
}
