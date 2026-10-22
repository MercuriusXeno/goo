package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.data.RemainderLedger;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.fraction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A drink paying after its player has left keeps the part under 1 mB for that
 * player, handed to their own ledger when they return.
 * decision every-producer-consumer-and-container-holds-a-remainder-buffer
 */
class OfflineRemaindersTest {

    private final UUID absent = UUID.randomUUID();
    private final OfflineRemainders offline = new OfflineRemainders();

    @Test
    void anAbsentPlayersPayoutDropsTheWholeAndKeepsTheRemainderForThem() {
        GooContents dropped = Drinks.payoutThrough(fraction(GooTypes.ROCK, 64, 3), offline.ledgerOf(absent));

        assertEquals(Map.of(GooTypes.ROCK, 21), dropped.getAll());
        assertEquals(GooFraction.of(1, 3), offline.ledgerOf(absent).remainders().of(GooTypes.ROCK));
    }

    @Test
    void aReturningPlayerReadsTheKeptRemainderOnTheirOwnLedger() {
        Drinks.payoutThrough(fraction(GooTypes.ROCK, 64, 3), offline.ledgerOf(absent));
        RemainderLedger returning = new RemainderLedger();

        GooContents paid = offline.handOver(absent, returning);

        assertTrue(paid.getAll().isEmpty());
        assertEquals(GooFraction.of(1, 3), returning.remainders().of(GooTypes.ROCK));
        assertTrue(offline.ledgerOf(absent).remainders().isEmpty());
    }

    @Test
    void keptAndHeldRemaindersMakingAWholeMbPayIt() {
        Drinks.payoutThrough(fraction(GooTypes.ROCK, 64, 3), offline.ledgerOf(absent));
        RemainderLedger returning = new RemainderLedger(GooRemainders.EMPTY.with(GooTypes.ROCK, GooFraction.of(2, 3)));

        GooContents paid = offline.handOver(absent, returning);

        assertEquals(Map.of(GooTypes.ROCK, 1), paid.getAll());
        assertTrue(returning.remainders().isEmpty());
    }
}
