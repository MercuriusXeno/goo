package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.data.PalmBuffers;
import com.mercuriusxeno.goo.network.PalmBufferPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Each caster's palm blob buffer for the ability selected in hand, as the
 * server last synced it: the fill then, the refill a tick and the most it
 * refills to. Between syncs the drawn fill grows at the synced speed up to
 * the cap, so the refill reads as growth and not as steps.
 * decision palm-blob-and-cutaway-cuboid-size-by-the-buffers-volume
 */
public final class ClientPalmBuffers {

    /** Each caster's last sync, by entity id. */
    private static final Map<Integer, Sync> SYNCS = new HashMap<>();

    private ClientPalmBuffers() {
    }

    /**
     * One sync, in half percents.
     *
     * @param fill  the fill
     * @param speed the refill a tick
     * @param cap   the most the fill refills to
     * @param at    the game time it stood at
     */
    private record Sync(int fill, int speed, int cap, long at) {
    }

    /**
     * Takes the server's fill for a caster's selected buffer.
     *
     * @param casterId the caster's entity id
     * @param fill     the fill, in half percents, or below 0 for a selection with no buffer
     * @param speed    the refill a tick, in half percents
     * @param cap      the most the fill refills to, in half percents
     * @param now      the game time the fill stood at
     */
    static void synced(int casterId, int fill, int speed, int cap, long now) {
        if (fill < 0) {
            SYNCS.remove(casterId);
        } else {
            SYNCS.put(casterId, new Sync(fill, speed, cap, now));
        }
    }

    /**
     * Whether the server has synced a buffer for a caster.
     *
     * @param casterId the caster's entity id
     * @return true where a buffer stands synced
     */
    static boolean holdsABuffer(int casterId) {
        return SYNCS.containsKey(casterId);
    }

    /**
     * A caster's fill at a time, grown from the last sync at its speed up to its cap.
     *
     * @param casterId the caster's entity id
     * @param now      the game time, with the partial tick
     * @return the fill, 0 to 1, whole for a caster with no buffer synced
     */
    static double fillAt(int casterId, double now) {
        Sync sync = SYNCS.get(casterId);
        if (sync == null) {
            return 1;
        }
        double grown = sync.fill() >= sync.cap() ? sync.fill()
                : Math.min(sync.cap(), sync.fill() + sync.speed() * Math.max(0, now - sync.at()));
        return grown / PalmBuffers.FULL;
    }

    /**
     * Takes the buffer the server sent for a caster.
     *
     * @param payload the buffer
     * @param context the network context
     */
    public static void onPayload(PalmBufferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                synced(payload.casterId(), payload.fill(), payload.speed(), payload.cap(), level.getGameTime());
            }
        });
    }

    /** Drops every caster's buffer, as a disconnect does. */
    public static void clear() {
        SYNCS.clear();
    }
}