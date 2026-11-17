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
 * and the clock runs on beneath it, forming knives back up to the charge's
 * most at its rate, each refilled knife paid as it forms. Left click held
 * throws a knife as it goes down and one every 2 ticks after, while a knife
 * is held, until it lifts.
 * A hold ends when the client lets go, the player leaves, or the glove leaves
 * the hand, refunding the price of every knife still held, and its end sends
 * a count of 0.
 * decision knives-charge-one-per-three-ticks-up-to-ten
 * decision knives-form-on-right-click-and-throw-on-left-click
 * decision knives-pay-per-knife-as-each-forms
 * decision knives-refill-from-the-stage-the-fan-rounds-up-to
 * decision knives-throw-one-per-two-ticks-while-left-click-is-held
 */
public final class KnifeHolds {

    private static final int NOT_THROWING = -1;
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
        /** The server tick the knife forming now began at; the cap stops the clock, holding it at the tick. */
        private int clockedFrom;
        /** The knives the clock has formed, less those thrown. */
        private int clocked;
        /** The server tick left click went down, NOT_THROWING while it is up. */
        private int throwingSince = NOT_THROWING;

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
            runTheClock(now);
            while (paid - thrown < clocked && AbilityCharges.charge(player, held.gooType(), price)) {
                paid++;
            }
            return paid - thrown;
        }

        /**
         * Forms each knife whose ticks have gone by since the last one,
         * keeping the ticks the next has run; at the cap the clock stands
         * still, so the knife after a throw from a full hold starts at the throw.
         * decision knives-throw-one-per-two-ticks-while-left-click-is-held: charging runs on beneath the throw
         *
         * @param now the server tick
         */
        private void runTheClock(int now) {
            int reached = charge().stackedSinceThrow(clocked, now - clockedFrom);
            if (reached >= charge().stack()) {
                clockedFrom = now;
            } else {
                clockedFrom += (reached - clocked) * charge().stackTicks();
            }
            clocked = reached;
        }

        /**
         * Takes thrown knives out of the hold, the knife forming now running on.
         *
         * @param knives the knives thrown
         * @param now    the server tick
         */
        void threw(int knives, int now) {
            if (knives <= 0) {
                return;
            }
            settle(now);
            thrown += Math.min(knives, paid - thrown);
            clocked = paid - thrown;
        }

        /**
         * Whether the held left click throws this tick: every beat its charge
         * names after the press, 2 ticks for Knives and 5 for Dart, the press's own
         * throw landing as it went down.
         * decision knives-throw-one-per-two-ticks-while-left-click-is-held
         * decision dart-throws-one-javelin-per-five-ticks-while-left-click-is-held
         *
         * @param now the server tick
         * @return true on a tick the held throw throws
         */
        boolean throwsAt(int now) {
            int since = now - throwingSince;
            int beat = charge().throwTicks();
            return throwingSince != NOT_THROWING && beat > 0 && since > 0 && since % beat == 0;
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
                syncCount(ended, 0);
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
     * Records left click going down or lifting during the player's hold.
     * decision knives-throw-one-per-two-ticks-while-left-click-is-held
     *
     * @param player the player
     * @param down   true as it goes down, false as it lifts
     */
    public void leftClick(ServerPlayer player, boolean down) {
        Hold hold = holds.get(player.getUUID());
        if (hold != null) {
            hold.throwingSince = down ? player.level().getServer().getTickCount() : NOT_THROWING;
        }
    }

    /**
     * The knives the player's standing hold has thrown.
     *
     * @param player the player
     * @return the knives thrown, 0 for a player holding none
     */
    public int thrownBy(ServerPlayer player) {
        Hold hold = holds.get(player.getUUID());
        return hold == null ? 0 : hold.thrown;
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
     * glove ends, refunded, a held left click throws on its beat, and a count
     * that changed goes to the watchers.
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
                syncCount(hold, 0);
                continue;
            }
            if (hold.throwsAt(now)) {
                KnivesThrowHandler.throwKnife(hold.player);
            }
            int formed = hold.settle(now);
            if (hold.syncs(formed)) {
                syncCount(hold, formed);
            }
        }
    }

    private static void syncCount(Hold hold, int formed) {
        EntityVisuals.sendToWatchers(hold.player, new StackFormedPayload(hold.player.getId(), formed,
                hold.held.ability().delivery().stacksJavelins()));
    }

    /** Drops every hold, as a server stop does. */
    public void clear() {
        holds.clear();
    }
}
