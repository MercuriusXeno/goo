package com.mercuriusxeno.goo.ability;

import net.minecraft.world.item.ItemUseAnimation;
import org.jspecify.annotations.Nullable;

/**
 * The eat route a self + brew ability takes: one wearing the brew badge on a
 * self delivery starts the player eating the glove, and the cost drains and
 * the program runs when the eat finishes, so letting go or switching items
 * before then runs nothing and drains nothing. A self ability wearing the
 * self badge runs on command. The route is decided by badge and delivery
 * kind alone, never by ability name, so an ability added later wearing the
 * brew badge on a self delivery eats with no further change. Every read here
 * takes no level, so a unit test reaches it whole.
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
     * Whether an ability takes the eat route.
     *
     * @param delivery the ability's delivery, or null where the glove holds no selection
     * @param badge    the ability's badge, or null where the glove holds no selection
     * @return true for the brew badge on a self delivery
     */
    public static boolean eats(@Nullable Delivery delivery, @Nullable AbilityBadge badge) {
        return delivery != null && delivery.kind() == DeliveryKind.SELF && badge == AbilityBadge.BREW;
    }

    /**
     * The use animation the glove reports.
     *
     * @param eats whether the selected ability takes the eat route
     * @return the eat for an eaten ability, none for every other
     */
    public static ItemUseAnimation animation(boolean eats) {
        return eats ? ItemUseAnimation.EAT : ItemUseAnimation.NONE;
    }

    /**
     * The use duration the glove reports.
     *
     * @param eats whether the selected ability takes the eat route
     * @return the vanilla eat duration for an eaten ability, zero for every other
     */
    public static int useDuration(boolean eats) {
        return eats ? EAT_TICKS : NO_USE;
    }

    /**
     * Runs the invoke when an eat finishes under an eaten ability; a finish
     * under any other ability runs nothing.
     *
     * @param delivery the ability's delivery
     * @param badge    the ability's badge
     * @param invoke   the drain and the program run
     */
    public static void finish(@Nullable Delivery delivery, @Nullable AbilityBadge badge, Runnable invoke) {
        if (eats(delivery, badge)) {
            invoke.run();
        }
    }
}
