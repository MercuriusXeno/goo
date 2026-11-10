package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A returning player's owed remainders go straight to their inventory on
 * login, the whole mB they make up handed over and the store forgetting them.
 * decision unmake-and-everything-only-it-used-deleted
 */
class OfflineRemainderEventsTest {

    private final UUID returning = UUID.randomUUID();
    private final OfflineRemainders offline = new OfflineRemainders();
    private final List<GooContents> inventory = new ArrayList<>();

    @Test
    void anOwedRemainderMakingAWholeMbLandsInTheReturningPlayersInventory() {
        offline.ledgerOf(returning).creditAllExact(fraction(GooTypes.ROCK, 64, 3).exactAll());
        RemainderLedger own = new RemainderLedger(GooRemainders.EMPTY.with(GooTypes.ROCK, GooFraction.of(2, 3)));

        OfflineRemainderEvents.handOver(offline, returning, own, inventory::add);

        assertEquals(List.of(Map.of(GooTypes.ROCK, 1)), inventory.stream().map(GooContents::getAll).toList());
        assertTrue(offline.ledgerOf(returning).remainders().isEmpty());
    }

    @Test
    void aPlayerOwedNothingIsHandedNothing() {
        OfflineRemainderEvents.handOver(offline, returning, new RemainderLedger(), inventory::add);

        assertTrue(inventory.isEmpty());
    }
}
