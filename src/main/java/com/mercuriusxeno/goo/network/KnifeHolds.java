package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Each player's Knives hold on the server: the tick it began, the charge it
 * forms by and the price of one knife, so the knives formed are the server's
 * count, read off the server's own clock rather than any client's. Each knife
 * the clock forms is paid as it forms, and a knife the player cannot cover
 * does not form. Each tick a count that changed goes to the caster and the
 * players watching; a hold ends when the client lets go, the player leaves,
 * or the glove leaves the hand, refunding the price of every knife it holds,
 * and its end sends a count of 0.
 * decision knives-form-in-fibonacci-stages-to-twelve
 * decision knives-stage-takes-twelve-ticks-with-no-cooldown
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-pay-per-knife-as-each-forms
 */
public final class KnifeHolds {

    private final Map<UUID, Hold> holds = new HashMap<>();

    /** One player's hold, the knives it has paid for counted as they form. */
    private static final class Hold {
        private final ServerPlayer player;
        private final int beganAt;
        private final Charge charge;
        private final ResourceKey<GooTypeDefinition> gooType;
        private final GooFraction price;
        private int paid;
        private int synced;

        Hold(ServerPlayer player, int beganAt, Charge charge, ResourceKey<GooTypeDefinition> gooType,
             GooFraction price) {
            this.player = player;
            this.beganAt = beganAt;
            this.charge = charge;
            this.gooType = gooType;
            this.price = price;
        }

        /**
         * Pays for each knife the clock has formed by now and no knife paid
         * for yet, stopping at the first the player cannot cover.
         *
         * @param now the server tick
         * @return the knives formed, every one paid for
         */
        int settle(int now) {
            int clocked = charge.knivesFormedAt(now - beganAt);
            while (paid < clocked && AbilityCharges.charge(player, gooType, price)) {
                paid++;
            }
            return paid;
        }

        /** Returns the price of every knife the hold holds. */
        void refund() {
            if (paid > 0) {
                AbilityCharges.refund(player, gooType, price.times(paid));
                paid = 0;
            }
        }
    }

    /**
     * Begins a hold for the player on this tick, refunding any it held.
     *
     * @param player  the holding player
     * @param charge  the held ability's charge
     * @param gooType the goo type a knife is paid in
     * @param price   the exact price of one knife
     */
    public void begin(ServerPlayer player, Charge charge, ResourceKey<GooTypeDefinition> gooType,
                      GooFraction price) {
        Hold was = holds.put(player.getUUID(),
                new Hold(player, player.level().getServer().getTickCount(), charge, gooType, price));
        if (was != null) {
            was.refund();
        }
    }

    /**
     * Ends the player's hold, its knives gone, none thrown, and every one refunded.
     *
     * @param player the player letting go
     */
    public void end(ServerPlayer player) {
        Hold ended = holds.remove(player.getUUID());
        if (ended != null) {
            ended.refund();
            if (ended.synced > 0) {
                syncCount(player, 0);
            }
        }
    }

    /**
     * The knives the player's hold has formed now, each paid for.
     *
     * @param player the player
     * @return the knives, 0 for a player holding none
     */
    public int formed(ServerPlayer player) {
        Hold hold = holds.get(player.getUUID());
        return hold == null ? 0 : hold.settle(player.level().getServer().getTickCount());
    }

    /**
     * Ticks every hold: a hold whose player has left or no longer holds a
     * glove ends, refunded, and a count that changed goes to the watchers.
     *
     * @param now the server tick
     */
    public void tick(int now) {
        Iterator<Map.Entry<UUID, Hold>> live = holds.entrySet().iterator();
        while (live.hasNext()) {
            Hold hold = live.next().getValue();
            if (hold.player.isRemoved() || !GooThrowHandler.validateGlove(hold.player)) {
                live.remove();
                hold.refund();
                syncCount(hold.player, 0);
                continue;
            }
            int formed = hold.settle(now);
            if (formed != hold.synced) {
                hold.synced = formed;
                syncCount(hold.player, formed);
            }
        }
    }

    private static void syncCount(ServerPlayer player, int formed) {
        EntityVisuals.sendToWatchers(player, new KnivesFormedPayload(player.getId(), formed));
    }

    /** Drops every hold, as a server stop does. */
    public void clear() {
        holds.clear();
    }
}
