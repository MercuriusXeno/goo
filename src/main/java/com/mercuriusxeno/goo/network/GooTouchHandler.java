package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;

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
         * @param cost the exact cost in mB
         * @return true when the toucher can pay it
         */
        boolean canAfford(GooFraction cost);

        /**
         * Drains the cost from the toucher's inventory.
         *
         * @param cost the exact cost in mB, the toucher's remainder paying first
         */
        void deplete(GooFraction cost);

        /**
         * Runs the ability on the touched entity; the toucher's client poses
         * the arm (decision use-key-throw-swings-no-more).
         */
        void strike();
    }

    /**
     * Whether an ability lands by touch on an entity within reach: an arc or
     * a beam aiming as a mob ability, Dart's held javelin among them.
     * decision mob-ability-touches-at-reach
     * decision dart-forms-on-right-click-and-throws-on-left-click
     *
     * @param delivery the ability's delivery
     * @param badge    the ability's badge
     * @return true for a mob ability that flies a line
     */
    public static boolean touchesAtReach(Delivery delivery, AbilityBadge badge) {
        return badge.aimsAs(delivery.kind()) == AbilityBadge.MOB && delivery.aimsALine();
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
     * Lands a touch the toucher can afford: the cost drains, then the strike
     * runs. A cost the toucher cannot pay lands nothing and drains nothing.
     *
     * @param cost    the touch's cost in mB
     * @param landing what the touch does to the world
     * @return true when the touch landed
     */
    static boolean landIfAffordable(GooFraction cost, TouchLanding landing) {
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
        public boolean canAfford(GooFraction cost) {
            return AbilityCharges.affords(player, gooType, cost);
        }

        @Override
        public void deplete(GooFraction cost) {
            AbilityCharges.charge(player, gooType, cost);
            GooThrowHandler.consumeReagents(player, payload.abilityId(), gooType);
        }

        @Override
        public void strike() {
            GooEffectScheduler.applyEffect(new PendingEffect(0, player.level(), player, gooType,
                    payload.targetEntityId(), payload.targetPos(),
                    GooThrowHandler.directionFromOrdinal(payload.targetFace()), payload.abilityId()));
        }
    }
}
