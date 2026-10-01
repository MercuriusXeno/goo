package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Server side of a punch delivery: the glove hand strikes the entity or
 * block within reach and the ability runs on what it struck in the same
 * tick, with no flight (decision punch-strikes-at-reach).
 */
public final class GooPunchHandler {

    private static final String LOG_OUT_OF_REACH = "Punch rejected: target {} blocks away, reach {}";

    private GooPunchHandler() {
    }

    /**
     * What a punch within reach does to the world, named so the reach check
     * runs without a live player.
     */
    interface PunchLanding {

        /**
         * Whether the puncher holds the cost.
         *
         * @param cost the cost in mB
         * @return true when the puncher can pay it
         */
        boolean canAfford(int cost);

        /**
         * Drains the cost from the puncher's inventory.
         *
         * @param cost the cost in mB
         */
        void deplete(int cost);

        /**
         * Swings the glove hand and runs the ability on what it struck.
         */
        void strike();
    }

    /**
     * The reach a punch strikes within: the delivery's range, or the
     * player's entity reach where the delivery names none.
     *
     * @param delivery    the punch delivery
     * @param playerReach the player's entity interaction range
     * @return the reach in blocks
     */
    public static double reach(Delivery delivery, double playerReach) {
        return delivery.range() > 0 ? delivery.range() : playerReach;
    }

    /**
     * Lands a punch whose target stands within reach and the puncher can
     * afford: the cost drains and the strike runs. A target beyond reach,
     * or a cost the puncher cannot pay, lands nothing and drains nothing.
     *
     * @param distanceSquared the squared distance from the puncher to the target
     * @param reach           the reach in blocks
     * @param cost            the punch's cost in mB
     * @param landing         what the punch does to the world
     * @return true when the punch landed
     */
    static boolean landIfInReach(double distanceSquared, double reach, int cost, PunchLanding landing) {
        if (distanceSquared > reach * reach) {
            if (Goo.LOGGER.isDebugEnabled()) {
                Goo.LOGGER.debug(LOG_OUT_OF_REACH, Math.sqrt(distanceSquared), reach);
            }
            return false;
        }
        if (!landing.canAfford(cost)) {
            return false;
        }
        landing.deplete(cost);
        landing.strike();
        return true;
    }

    /**
     * Punches the payload's target with the ability.
     *
     * @param player  the punching player
     * @param payload the throw payload naming the target
     * @param gooType the ability's goo type
     * @param ability the punch ability
     */
    static void punch(ServerPlayer player, GooThrowPayload payload, ResourceKey<GooTypeDefinition> gooType,
                      AbilityDefinition ability) {
        double reach = reach(ability.delivery(), player.entityInteractionRange());
        landIfInReach(GooThrowHandler.targetDistanceSquared(player, payload), reach,
                GooThrowHandler.resolveThrowCost(player, payload, gooType),
                new PlayerPunchLanding(player, payload, gooType));
    }

    /**
     * A punch landing on a live player's world: the goo drains from the
     * player's inventory and the struck target takes the ability through
     * the same impact an arrived throw runs.
     *
     * @param player  the punching player
     * @param payload the throw payload naming the target
     * @param gooType the ability's goo type
     */
    private record PlayerPunchLanding(ServerPlayer player, GooThrowPayload payload,
                                      ResourceKey<GooTypeDefinition> gooType) implements PunchLanding {

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
