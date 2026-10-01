package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Server side of a touch: a mob ability aimed at an entity within the
 * player's reach lands on it at once, the cost drained and the program run
 * in the same tick with no flight; beyond reach it throws.
 * decision mob-ability-touches-at-reach
 */
public final class GooTouchHandler {

    private GooTouchHandler() {
    }

    /**
     * What a touch does to the world, named so the landing runs without a
     * live player.
     */
    interface TouchLanding {

        /**
         * Whether the toucher holds the cost.
         *
         * @param cost the cost in mB
         * @return true when the toucher can pay it
         */
        boolean canAfford(int cost);

        /**
         * Drains the cost from the toucher's inventory.
         *
         * @param cost the cost in mB
         */
        void deplete(int cost);

        /**
         * Swings the glove hand and runs the ability on the touched entity.
         */
        void strike();
    }

    /**
     * Whether an ability lands by touch on an entity within reach: an arc or
     * a beam wearing the mob badge.
     * decision mob-ability-touches-at-reach
     *
     * @param delivery the ability's delivery
     * @param badge    the ability's badge
     * @return true for a mob ability that flies a line
     */
    public static boolean touchesAtReach(Delivery delivery, AbilityBadge badge) {
        return badge == AbilityBadge.MOB && delivery.aimsALine();
    }

    /**
     * Whether a throw lands as a touch: a mob ability aimed at an entity
     * standing within reach. Client and server both read this rule, so the
     * client counts in flight exactly the throws the server flies.
     *
     * @param delivery        the ability's delivery
     * @param badge           the ability's badge
     * @param entityTarget    whether the throw aims at an entity
     * @param distanceSquared the squared distance from the player's feet to the entity's
     * @param reach           the player's entity interaction range
     * @return true when the throw touches rather than flies
     */
    public static boolean touches(Delivery delivery, AbilityBadge badge, boolean entityTarget,
                                  double distanceSquared, double reach) {
        return entityTarget && touchesAtReach(delivery, badge) && distanceSquared <= reach * reach;
    }

    /**
     * What an attack-key press aims with, read the same way on the client
     * that sends it and in a gametest that drives it.
     *
     * @param gloveInHand    whether the hand the attack key presses with holds a glove
     * @param delivery       the glove's selected ability's delivery, or null when none
     * @param badge          the glove's selected ability's badge, or null when none
     * @param target         the aimed entity, or null when the aim holds a block or nothing
     * @param playerPosition the player's feet
     * @param reach          the player's entity interaction range
     */
    public record AttackPress(boolean gloveInHand, @Nullable Delivery delivery, @Nullable AbilityBadge badge,
                              @Nullable Entity target, Vec3 playerPosition, double reach) {
    }

    /**
     * Whether an attack-key press sends the touch beside the vanilla melee
     * hit: a glove in the pressing hand, a mob ability selected and an
     * entity aimed within reach.
     * decision attack-key-touches-plus-punches
     *
     * @param press what the press aims with
     * @return true when the press touches
     */
    public static boolean attackTouches(AttackPress press) {
        Entity target = press.target();
        if (!press.gloveInHand() || target == null) {
            return false;
        }
        return press.delivery() != null && press.badge() != null && touches(press.delivery(), press.badge(), true,
                press.playerPosition().distanceToSqr(target.position()), press.reach());
    }

    /**
     * Lands a touch the toucher can afford: the cost drains, then the strike
     * runs. A cost the toucher cannot pay lands nothing and drains nothing.
     *
     * @param cost    the touch's cost in mB
     * @param landing what the touch does to the world
     * @return true when the touch landed
     */
    static boolean landIfAffordable(int cost, TouchLanding landing) {
        if (!landing.canAfford(cost)) {
            return false;
        }
        landing.deplete(cost);
        landing.strike();
        return true;
    }

    /**
     * Touches the payload's target entity with the ability.
     *
     * @param player  the touching player
     * @param payload the throw payload naming the target
     * @param gooType the ability's goo type
     */
    static void touch(ServerPlayer player, GooThrowPayload payload, ResourceKey<GooTypeDefinition> gooType) {
        landIfAffordable(GooThrowHandler.resolveThrowCost(player, payload, gooType),
                new PlayerTouchLanding(player, payload, gooType));
    }

    /**
     * A touch landing on a live player's world: the goo drains from the
     * player's inventory and the touched entity takes the ability through
     * the same impact an arrived throw runs.
     *
     * @param player  the touching player
     * @param payload the throw payload naming the target
     * @param gooType the ability's goo type
     */
    private record PlayerTouchLanding(ServerPlayer player, GooThrowPayload payload,
                                      ResourceKey<GooTypeDefinition> gooType) implements TouchLanding {

        @Override
        public boolean canAfford(int cost) {
            return GooSourceScanner.hasEnough(player, gooType, cost);
        }

        @Override
        public void deplete(int cost) {
            GooSourceScanner.deplete(player, gooType, cost);
        }

        @Override
        public void strike() {
            player.swing(gloveHand(player), true);
            GooEffectScheduler.applyEffect(new PendingEffect(0, player.level(), player, gooType,
                    payload.targetEntityId(), payload.targetPos(),
                    GooThrowHandler.directionFromOrdinal(payload.targetFace()), payload.abilityId()));
        }

        private static InteractionHand gloveHand(ServerPlayer player) {
            return player.getMainHandItem().getItem() instanceof GooGloveItem
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        }
    }
}
