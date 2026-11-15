package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooRemainders;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A broken crucible drops every reservoir type save infinity, which is lost with it
 * (decision infinity-sources-whatever-shares-its-vessel).
 */
class CrucibleDropsTest {

    @Test
    void aBrokenCrucibleDropsEveryTypeButInfinity() {
        GooContents reservoir = new GooContents(Map.of(GooTypes.INFINITY, 1, GooTypes.BLAZE, 400));

        assertEquals(Map.of(GooTypes.BLAZE, 400), CrucibleDrops.droppedReservoir(reservoir).getAll());
    }

    /** Infinity's remainder stays with the crucible too, so no part of infinity spills. */
    @Test
    void aBrokenCrucibleDropsEveryRemainderButInfinitys() {
        GooRemainders remainders = GooRemainders.EMPTY.with(GooTypes.INFINITY, GooFraction.of(1, 2))
                .with(GooTypes.BLAZE, GooFraction.of(1, 3));
        GooContents reservoir = new GooContents(Map.of(GooTypes.BLAZE, 400), remainders);

        assertEquals(GooRemainders.EMPTY.with(GooTypes.BLAZE, GooFraction.of(1, 3)),
                CrucibleDrops.droppedReservoir(reservoir).remainders());
    }
}
