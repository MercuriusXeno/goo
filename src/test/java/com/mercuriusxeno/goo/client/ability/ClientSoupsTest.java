package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.network.SoupPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client keeps a soup while the server shows it and drops it once it
 * turns into goo items (decision unmake-waves-dissolve-by-crucible-cost).
 */
class ClientSoupsTest {

    private static SoupPayload soup(boolean open) {
        return new SoupPayload(7, open, true, Vec3.ZERO, Map.of(GooTypes.ROCK, 1152), List.of());
    }

    @Test
    void aShownSoupIsDrawnUntilItGoesStale() {
        ClientSoups soups = new ClientSoups();
        soups.show(soup(true), 100);

        assertEquals(1, soups.live(100 + ClientSoups.STALE_TICKS).size());
        assertTrue(soups.live(101 + ClientSoups.STALE_TICKS).isEmpty());
    }

    @Test
    void aSoupTurnedIntoItemsIsDroppedAtOnce() {
        ClientSoups soups = new ClientSoups();
        soups.show(soup(true), 100);
        soups.show(soup(false), 101);

        assertTrue(soups.live(101).isEmpty());
    }
}
