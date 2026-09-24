package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Map;
import static com.mercuriusxeno.goo.block.crucible.CrucibleCapacity.TYPE_CAPACITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests the crucible's per-type cap: how many whole units fit, the melt pool
 * refusing a merge past the cap, and the melt drain leaving a full type in
 * the pool (decision crucible-refuses-past-two-billion).
 */
class CrucibleCapacityTest {

    private static final int UNIT = 1_000;

    /**
     * Whole units of rock fit up to the cap and no further.
     *
     * @param heldRock the rock the store holds
     * @param offered  the units offered
     * @param expected the units that fit
     */
    @ParameterizedTest
    @CsvSource({
        "0,          5, 5",
        "1999997500, 5, 2",
        "1999999000, 5, 1",
        "1999999001, 5, 0",
        "2000000000, 5, 0",
    })
    void wholeUnitsThatFit(int heldRock, int offered, int expected) {
        GooContents held = new GooContents(Map.of(GooTypes.ROCK, heldRock));
        GooContents perUnit = new GooContents(Map.of(GooTypes.ROCK, UNIT));
        assertEquals(expected, CrucibleCapacity.wholeUnitsThatFit(held, perUnit, offered));
    }

    /** A unit of two types fits only as often as its tighter type allows. */
    @Test
    void wholeUnitsThatFit_tighterTypeLimits() {
        GooContents held = new GooContents(Map.of(GooTypes.METAL, TYPE_CAPACITY - UNIT));
        GooContents perUnit = new GooContents(Map.of(GooTypes.ROCK, UNIT, GooTypes.METAL, UNIT));
        assertEquals(1, CrucibleCapacity.wholeUnitsThatFit(held, perUnit, 5));
    }

    /** A pool whose rock is at the cap refuses one more mB, and its rock stays at the cap. */
    @Test
    void mergedWithinCap_refusesPastTheCap() {
        GooContents pool = new GooContents(Map.of(GooTypes.ROCK, TYPE_CAPACITY));
        assertNull(CrucibleCapacity.mergedWithinCap(pool, new GooContents(Map.of(GooTypes.ROCK, 1))));
        assertEquals(TYPE_CAPACITY, pool.getVolume(GooTypes.ROCK));
    }

    /** A merge that lands exactly on the cap is taken whole. */
    @Test
    void mergedWithinCap_takesUpToTheCap() {
        GooContents pool = new GooContents(Map.of(GooTypes.ROCK, TYPE_CAPACITY - UNIT));
        GooContents merged = CrucibleCapacity.mergedWithinCap(pool, new GooContents(Map.of(GooTypes.ROCK, UNIT)));
        assertEquals(TYPE_CAPACITY, merged.getVolume(GooTypes.ROCK));
    }

    /**
     * With the reservoir full of rock, a tick's drain leaves the pool's rock
     * whole and takes metal's share.
     */
    @Test
    void drainAccepted_fullTypeStaysInThePool() {
        GooContents pool = new GooContents(Map.of(GooTypes.ROCK, UNIT, GooTypes.METAL, UNIT));
        Map<ResourceKey<GooTypeDefinition>, Integer> shares = CrucibleMath.computeDrainShares(pool, 10);

        GooContents drained = CrucibleCapacity.drainAccepted(pool, shares,
            (type, amount) -> type.equals(GooTypes.ROCK) ? 0 : amount);

        assertEquals(UNIT, drained.getVolume(GooTypes.ROCK));
        assertEquals(UNIT - shares.get(GooTypes.METAL), drained.getVolume(GooTypes.METAL));
    }
}
