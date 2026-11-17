package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Each player's Knives hold on the server: the tick it began, the charge it
 * forms by and the price of one knife, so the knives formed are the server's
 * count, read off the server's own clock rather than any client's. Each knife
 * the clock forms is paid as it forms, and a knife the player cannot cover
 * does not form. Each tick a count that changed goes to the caster and the
 * players watching. A throw takes knives out of the hold, their prices spent,
 * and restarts the clock from the knives left, which form back up to the
 * charge's most at its rate, each refilled knife paid as it forms.
 * A hold ends when the client lets go, the player leaves, or the glove leaves
 * the hand, refunding the price of every knife still held, and its end sends
 * a count of 0.
 * decision knives-charge-one-per-three-ticks-up-to-ten
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-pay-per-knife-as-each-forms
 * decision knives-refill-from-the-stage-the-fan-rounds-up-to
 */
public final class KnifeHolds {

    private final Map<UUID, Hold> holds = new HashMap<>();

    /**
     * What a standing hold throws with.
     *
     * @param gooType the held ability's goo type
     * @param ability the held ability
     */
    public record Held(ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability) {
    }

    /** One player's hold, the knives it has paid for counted as they form, and those it threw. */
    private static final class Hold {
        private final ServerPlayer player;
        private final Held held;
        private final GooFraction price;
        private int paid;
        private int thrown;
        private int synced;
        /** The server tick the clock counts from: the hold's start, then each throw. */
        private int clockedFrom;
        /** The knives held as the clock started, which it forms on from. */
        private int heldAtClock;

        Hold(ServerPlayer player, int beganAt, Held held, GooFraction price) {
            this.player = player;
            this.clockedFrom = beganAt;
            this.held = held;
            this.price = price;
        }

        Charge charge() {
            return held.ability().delivery().charge();
        }

        /**
         * Pays for each knife the clock has formed by now and no knife paid
         * for yet, stopping at the first the player cannot cover.
         *
         * @param now the server tick
         * @return the knives in the fan, every one paid for
         */
        int settle(int now) {
            int clocked = charge().knivesSinceThrow(heldAtClock, now - clockedFrom);
            while (paid - thrown < clocked && AbilityCharges.charge(player, held.gooType(), price)) {
                paid++;
            }
            return paid - thrown;
        }

        /**
         * Takes thrown knives out of the hold and restarts the clock from the
         * knives left.
         *
         * @param knives the knives thrown
         * @param now    the server tick
         */
        void threw(int knives, int now) {
            if (knives <= 0) {
                return;
            }
            thrown += Math.min(knives, paid - thrown);
            heldAtClock = paid - thrown;
            clockedFrom = now;
        }

        /**
         * Records a count to send where it differs from the last sent.
         *
         * @param formed the knives in the fan now
         * @return true where the count changed and goes to the watchers
         */
        boolean syncs(int formed) {
            boolean changed = formed != synced;
            synced = formed;
            return changed;
        }

        /** Returns the price of every knife still in the fan. */
        void refund() {
            if (paid > thrown) {
                AbilityCharges.refund(player, held.gooType(), price.times(paid - thrown));
                thrown = paid;
            }
        }
    }

    /**
     * Begins a hold for the player on this tick, refunding any it held.
     *
     * @param player the holding player
     * @param held   the held ability and its goo type, which a knife is paid in
     * @param price  the exact price of one knife
     */
    public void begin(ServerPlayer player, Held held, GooFraction price) {
        Hold was = holds.put(player.getUUID(),
                new Hold(player, player.level().getServer().getTickCount(), held, price));
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
     * What the player's standing hold throws with.
     *
     * @param player the player
     * @return the held ability and its goo type, empty for a player holding none
     */
    public Optional<Held> heldBy(ServerPlayer player) {
        return Optional.ofNullable(holds.get(player.getUUID())).map(hold -> hold.held);
    }

    /**
     * Takes thrown knives out of the player's fan, their prices spent.
     *
     * @param player the throwing player
     * @param knives the knives thrown, never more than the fan holds
     */
    public void threw(ServerPlayer player, int knives) {
        Hold hold = holds.get(player.getUUID());
        if (hold != null) {
            hold.threw(knives, player.level().getServer().getTickCount());
        }
    }

    /**
     * The knives in the player's fan now, each paid for.
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
            if (hold.syncs(formed)) {
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
