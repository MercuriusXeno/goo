package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.Charge;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Each player's Knives hold on the server: the tick it began and the charge
 * it forms by, so the knives formed are the server's count, read off the
 * server's own clock rather than any client's. Each tick the count reaching
 * a new stage goes to the caster and the players watching; a hold ends when
 * the client lets go, the player leaves, or the glove leaves the hand, and
 * its end sends a count of 0.
 * decision knives-form-in-fibonacci-stages-to-twelve
 * decision knives-stage-takes-twelve-ticks-with-no-cooldown
 * decision knives-form-on-right-click-and-throw-on-left-click
 */
public final class KnifeHolds {

    private final Map<UUID, Hold> holds = new HashMap<>();

    /**
     * One player's hold.
     *
     * @param player  the holding player
     * @param beganAt the server tick the hold began
     * @param charge  the held ability's charge
     * @param synced  the count last sent to the watchers
     */
    private record Hold(ServerPlayer player, int beganAt, Charge charge, int synced) {

        int formedAt(int now) {
            return charge.knivesFormedAt(now - beganAt);
        }
    }

    /**
     * Begins a hold for the player on this tick, in place of any it held.
     *
     * @param player the holding player
     * @param charge the held ability's charge
     */
    public void begin(ServerPlayer player, Charge charge) {
        holds.put(player.getUUID(), new Hold(player, player.level().getServer().getTickCount(), charge, 0));
    }

    /**
     * Ends the player's hold, its knives gone and none thrown.
     *
     * @param player the player letting go
     */
    public void end(ServerPlayer player) {
        Hold ended = holds.remove(player.getUUID());
        if (ended != null && ended.synced() > 0) {
            syncCount(player, 0);
        }
    }

    /**
     * The knives the player's hold has formed now.
     *
     * @param player the player
     * @return the knives, 0 for a player holding none
     */
    public int formed(ServerPlayer player) {
        Hold hold = holds.get(player.getUUID());
        return hold == null ? 0 : hold.formedAt(player.level().getServer().getTickCount());
    }

    /**
     * Ticks every hold: a hold whose player has left or no longer holds a
     * glove ends, and a count that changed goes to the watchers.
     *
     * @param now the server tick
     */
    public void tick(int now) {
        Iterator<Map.Entry<UUID, Hold>> live = holds.entrySet().iterator();
        while (live.hasNext()) {
            Map.Entry<UUID, Hold> entry = live.next();
            Hold hold = entry.getValue();
            ServerPlayer player = hold.player();
            if (player.isRemoved() || !GooThrowHandler.validateGlove(player)) {
                live.remove();
                syncCount(player, 0);
                continue;
            }
            int formed = hold.formedAt(now);
            if (formed != hold.synced()) {
                entry.setValue(new Hold(player, hold.beganAt(), hold.charge(), formed));
                syncCount(player, formed);
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
