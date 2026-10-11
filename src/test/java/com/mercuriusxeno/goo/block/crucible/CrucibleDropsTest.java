package com.mercuriusxeno.goo.block.crucible;

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
}
