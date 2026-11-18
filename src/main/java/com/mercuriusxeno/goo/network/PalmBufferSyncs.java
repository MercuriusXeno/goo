package com.mercuriusxeno.goo.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The palm blob buffer each player's clients were last told of, so a sync
 * goes out only when the server's fill strays from what that sync's speed
 * and cap grow it to: a weapon formed, a release, a cap or a speed changed,
 * or another selection.
 * decision palm-blob-and-cutaway-cuboid-size-by-the-buffers-volume
 */
public final class PalmBufferSyncs {

    private final Map<UUID, Told> told = new HashMap<>();

    /**
     * What a player's clients were told.
     *
     * @param abilityId the selected ability, empty for none
     * @param fill      the fill told, in half percents
     * @param speed     the refill a tick told
     * @param cap       the cap told
     * @param at        the server tick it was told
     */
    record Told(String abilityId, int fill, int speed, int cap, int at) {

        /**
         * The fill this sync grows to by a tick.
         *
         * @param now the server tick
         * @return the fill a client draws then
         */
        int grownTo(int now) {
            return fill >= cap ? fill : Math.min(cap, fill + speed * (now - at));
        }
    }

    /**
     * Tells each player's clients of the buffer of the ability selected in
     * hand, where it strays from what they were last told.
     *
     * @param server the server
     * @param holds  the standing holds, whose weapons held reserve their shares
     * @param now    the server tick
     */
    public void syncEvery(MinecraftServer server, KnifeHolds holds, int now) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Told current = PalmBuffer.toldNow(player, holds, now);
            Told last = told.get(player.getUUID());
            if (strays(last, current, now)) {
                told.put(player.getUUID(), current);
                EntityVisuals.sendToWatchers(player, new PalmBufferPayload(player.getId(), current.fill(),
                        current.speed(), current.cap()));
            }
        }
    }

    // Whether what the clients draw strays from the server's buffer now.
    static boolean strays(Told last, Told current, int now) {
        return last == null || !sameGrowth(last, current) || last.grownTo(now) != current.fill();
    }

    // Whether two syncs grow the same buffer the same way: one ability, one speed, one cap.
    private static boolean sameGrowth(Told last, Told current) {
        return last.abilityId().equals(current.abilityId()) && last.speed() == current.speed()
                && last.cap() == current.cap();
    }

    /** Forgets every player told, as a server stop does. */
    public void clear() {
        told.clear();
    }
}