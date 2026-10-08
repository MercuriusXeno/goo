package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.network.SoupPayload;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Unmake soups this client draws, each as the server last showed it, kept
 * while the server keeps showing it.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class ClientSoups {

    /** The store the soup handler and the soup renderer share. */
    public static final ClientSoups CLIENT = new ClientSoups();
    /** Ticks a soup the server stopped showing is still drawn, past a late packet. */
    static final int STALE_TICKS = 3;

    /**
     * One player's soup as the server last showed it.
     *
     * @param playerId  the player's entity id
     * @param held      whether the hold goes on, the ball riding the player's look
     * @param ball      where the server last placed the ball's middle
     * @param drunk     the goo it holds
     * @param streaming the blocks streaming into it
     * @param seen      the game time it was last shown
     */
    public record Soup(int playerId, boolean held, Vec3 ball, GooContents drunk,
                       List<SoupPayload.Streaming> streaming, long seen) {
    }

    private final Map<Integer, Soup> soups = new HashMap<>();

    /**
     * Keeps a soup as the server shows it, or drops it once it has turned into goo items.
     *
     * @param payload the soup this tick
     * @param now     the game time
     */
    public void show(SoupPayload payload, long now) {
        if (!payload.open()) {
            soups.remove(payload.playerId());
            return;
        }
        soups.put(payload.playerId(), new Soup(payload.playerId(), payload.held(), payload.ball(),
                new GooContents(payload.drunk()), List.copyOf(payload.streaming()), now));
    }

    /**
     * The soups to draw, forgetting each the server has stopped showing.
     *
     * @param now the game time
     * @return the soups
     */
    public List<Soup> live(long now) {
        soups.values().removeIf(soup -> now - soup.seen() > STALE_TICKS);
        return List.copyOf(soups.values());
    }
}
