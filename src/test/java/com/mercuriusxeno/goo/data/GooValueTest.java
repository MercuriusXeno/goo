package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.goo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GooValue: construction, arithmetic, and query methods.
 */
class GooValueTest {

    // ── Construction ────────────────────────────────────────────────────

    /**
     * Zero amounts are filtered out, but negatives are preserved.
     */
    @Test
    void constructorFiltersZeroButKeepsNegative() {
        Map<ResourceKey<GooTypeDefinition>, Integer> map = new HashMap<>();
        map.put(GooTypes.METAL, 0);
        map.put(GooTypes.CRYSTAL, -5);
        map.put(GooTypes.LEAF, 10);

        GooValue val = new GooValue(map);
        assertEquals(GooFraction.of(0), val.exact(GooTypes.METAL));
        assertEquals(GooFraction.of(-5), val.exact(GooTypes.CRYSTAL));
        assertEquals(GooFraction.of(10), val.exact(GooTypes.LEAF));
    }

    /**
     * Empty map produces an empty GooValue.
     */
    @Test
    void emptyMapProducesEmptyValue() {
        GooValue val = new GooValue(Map.of());
        assertTrue(val.isEmpty());
        assertEquals(GooFraction.of(0), val.totalExact());
    }

    /**
     * EMPTY singleton is truly empty.
     */
    @Test
    void emptySingletonIsEmpty() {
        assertTrue(GooValue.EMPTY.isEmpty());
        assertEquals(GooFraction.of(0), GooValue.EMPTY.totalExact());
        assertNull(GooValue.EMPTY.largestType());
    }

    // ── totalGoo ──────────────────────────────────────────────────────

    /**
     * Total goo sums all types.
     */
    @Test
    void totalGooSumsAllTypes() {
        GooValue val = goo(GooTypes.METAL, 5, GooTypes.CRYSTAL, 3);
        assertEquals(GooFraction.of(8), val.totalExact());
    }

    /**
     * Single type total goo equals that type's amount.
     */
    @Test
    void singleTypeTotalGoo() {
        GooValue val = goo(GooTypes.JELLY, 42);
        assertEquals(GooFraction.of(42), val.totalExact());
    }

    // ── largestType ──────────────────────────────────────────────────

    /**
     * Largest type is the one with the highest amount.
     */
    @Test
    void largestTypeIsHighestAmount() {
        GooValue val = goo(GooTypes.BLAZE, 10, GooTypes.LEAF, 5);
        assertEquals(GooTypes.BLAZE, val.largestType());
    }

    /**
     * Single-type value returns that type as largest.
     */
    @Test
    void singleTypeLargest() {
        GooValue val = goo(GooTypes.ENDER, 1);
        assertEquals(GooTypes.ENDER, val.largestType());
    }

    // ── add ─────────────────────────────────────────────────────────────

    /**
     * Adding two values sums their types.
     */
    @Test
    void addSumsTypes() {
        GooValue a = goo(GooTypes.METAL, 5);
        GooValue b = goo(GooTypes.METAL, 3);
        GooValue result = a.add(b, 1);
        assertEquals(GooFraction.of(8), result.exact(GooTypes.METAL));
    }

    /**
     * Adding with multiplier scales the added value.
     */
    @Test
    void addWithMultiplier() {
        GooValue a = goo(GooTypes.ROCK, 2);
        GooValue b = goo(GooTypes.ROCK, 3);
        GooValue result = a.add(b, 4);
        assertEquals(GooFraction.of(14), result.exact(GooTypes.ROCK)); // 2 + 3*4
    }

    /**
     * Adding introduces new types.
     */
    @Test
    void addIntroducesNewType() {
        GooValue a = goo(GooTypes.METAL, 5);
        GooValue b = goo(GooTypes.CRYSTAL, 3);
        GooValue result = a.add(b, 1);
        assertEquals(GooFraction.of(5), result.exact(GooTypes.METAL));
        assertEquals(GooFraction.of(3), result.exact(GooTypes.CRYSTAL));
    }

    /**
     * Adding to EMPTY gives the added value.
     */
    @Test
    void addToEmpty() {
        GooValue b = goo(GooTypes.GLOW, 7);
        GooValue result = GooValue.EMPTY.add(b, 1);
        assertEquals(GooFraction.of(7), result.exact(GooTypes.GLOW));
    }

    /**
     * Adding a value with negatives subtracts those types (exposed copper use case).
     */
    @Test
    void addWithNegativeSubtractsType() {
        GooValue copper = goo(GooTypes.METAL, 100, GooTypes.ROCK, 20);
        Map<ResourceKey<GooTypeDefinition>, Integer> exposedMap = new HashMap<>();
        exposedMap.put(GooTypes.YORE, 32);
        exposedMap.put(GooTypes.METAL, -64);
        GooValue exposed = new GooValue(exposedMap);

        GooValue result = copper.add(exposed, 1);
        assertEquals(GooFraction.of(36), result.exact(GooTypes.METAL));  // 100 - 64
        assertEquals(GooFraction.of(32), result.exact(GooTypes.YORE));    // 0 + 32
        assertEquals(GooFraction.of(20), result.exact(GooTypes.ROCK));    // unchanged
    }

    /**
     * Adding negatives that exceed the positive amount produces a negative result.
     */
    @Test
    void addWithNegativeCanGoNegative() {
        GooValue small = goo(GooTypes.METAL, 10);
        Map<ResourceKey<GooTypeDefinition>, Integer> bigDrain = new HashMap<>();
        bigDrain.put(GooTypes.METAL, -50);
        GooValue drain = new GooValue(bigDrain);

        GooValue result = small.add(drain, 1);
        assertEquals(GooFraction.of(-40), result.exact(GooTypes.METAL));
    }

    /**
     * floorZero clamps all negative types to zero.
     */
    @Test
    void floorZeroClampsNegatives() {
        Map<ResourceKey<GooTypeDefinition>, Integer> map = new HashMap<>();
        map.put(GooTypes.METAL, -40);
        map.put(GooTypes.YORE, 32);
        map.put(GooTypes.ROCK, 0);
        GooValue val = new GooValue(map);

        GooValue floored = val.floorZero();
        assertEquals(GooFraction.of(0), floored.exact(GooTypes.METAL));
        assertEquals(GooFraction.of(32), floored.exact(GooTypes.YORE));
        assertTrue(floored.exactAll().containsKey(GooTypes.YORE));
        assertFalse(floored.exactAll().containsKey(GooTypes.METAL));
    }

    // ── subtract ────────────────────────────────────────────────────────

    /**
     * Subtracting per-type produces the difference.
     */
    @Test
    void subtractPerType() {
        GooValue a = goo(GooTypes.METAL, 10, GooTypes.CRYSTAL, 8);
        GooValue b = goo(GooTypes.METAL, 3, GooTypes.CRYSTAL, 2);
        GooValue result = a.subtract(b);
        assertEquals(GooFraction.of(7), result.exact(GooTypes.METAL));
        assertEquals(GooFraction.of(6), result.exact(GooTypes.CRYSTAL));
    }

    /**
     * Subtracting more than available goes negative.
     */
    @Test
    void subtractCanGoNegative() {
        GooValue a = goo(GooTypes.METAL, 5);
        GooValue b = goo(GooTypes.METAL, 10);
        GooValue result = a.subtract(b);
        assertEquals(GooFraction.of(-5), result.exact(GooTypes.METAL));
    }

    /**
     * Subtracting from EMPTY produces negative values.
     */
    @Test
    void subtractFromEmptyGoesNegative() {
        GooValue b = goo(GooTypes.LEAF, 5);
        GooValue result = GooValue.EMPTY.subtract(b);
        assertEquals(GooFraction.of(-5), result.exact(GooTypes.LEAF));
    }

    /**
     * Subtracting a type not present in the source introduces a negative.
     */
    @Test
    void subtractMissingTypeGoesNegative() {
        GooValue a = goo(GooTypes.METAL, 10);
        GooValue b = goo(GooTypes.CRYSTAL, 5);
        GooValue result = a.subtract(b);
        assertEquals(GooFraction.of(10), result.exact(GooTypes.METAL));
        assertEquals(GooFraction.of(-5), result.exact(GooTypes.CRYSTAL));
    }

    // ── multiply ────────────────────────────────────────────────────────

    /**
     * Multiplying scales all types.
     */
    @Test
    void multiplyScalesAllTypes() {
        GooValue val = goo(GooTypes.METAL, 10, GooTypes.CRYSTAL, 6);
        GooValue result = val.multiply(3);
        assertEquals(GooFraction.of(30), result.exact(GooTypes.METAL));
        assertEquals(GooFraction.of(18), result.exact(GooTypes.CRYSTAL));
    }

    /**
     * Multiplying by 1 returns the same instance.
     */
    @Test
    void multiplyByOneReturnsSame() {
        GooValue val = goo(GooTypes.BLAZE, 15);
        assertSame(val, val.multiply(1));
    }

    /**
     * Multiplying by 0 returns EMPTY.
     */
    @Test
    void multiplyByZeroReturnsEmpty() {
        GooValue val = goo(GooTypes.JELLY, 100);
        assertTrue(val.multiply(0).isEmpty());
    }

    /**
     * Multiplying by negative returns EMPTY.
     */
    @Test
    void multiplyByNegativeReturnsEmpty() {
        GooValue val = goo(GooTypes.LEAF, 42);
        assertTrue(val.multiply(-1).isEmpty());
    }

    // ── divide ──────────────────────────────────────────────────────────

    /**
     * Dividing evenly produces exact result.
     */
    @Test
    void divideEvenly() {
        GooValue val = goo(GooTypes.METAL, 10);
        GooValue result = val.divide(2);
        assertEquals(GooFraction.of(5), result.exact(GooTypes.METAL));
    }

    /**
     * Dividing with a remainder keeps the exact fraction.
     */
    @Test
    void divideWithRemainderKeepsTheFraction() {
        GooValue val = goo(GooTypes.METAL, 7);
        GooValue result = val.divide(3);
        assertEquals(GooFraction.of(7, 3), result.exact(GooTypes.METAL));
    }

    /**
     * Dividing by 1 returns the same value.
     */
    @Test
    void divideByOneReturnsSame() {
        GooValue val = goo(GooTypes.BLAZE, 15);
        GooValue result = val.divide(1);
        assertSame(val, result);
    }

    /**
     * Dividing keeps a share under one rather than dropping the type.
     */
    @Test
    void divideKeepsAShareUnderOne() {
        GooValue val = goo(GooTypes.METAL, 1);
        GooValue result = val.divide(2);
        assertEquals(GooFraction.of(1, 2), result.exact(GooTypes.METAL));
    }

    /**
     * Multi-type divide divides each type independently.
     */
    @Test
    void multiTypeDivide() {
        GooValue val = goo(GooTypes.METAL, 10, GooTypes.CRYSTAL, 6);
        GooValue result = val.divide(3);
        assertEquals(GooFraction.of(10, 3), result.exact(GooTypes.METAL));
        assertEquals(GooFraction.of(2), result.exact(GooTypes.CRYSTAL));
    }

    // ── scale ──────────────────────────────────────────────────────────

    /**
     * Scaling by 0.5 halves all types (rounded).
     */
    @Test
    void scaleByHalf() {
        GooValue val = goo(GooTypes.BLAZE, 100, GooTypes.METAL, 50);
        GooValue result = val.scale(0.5);
        assertEquals(GooFraction.of(50), result.exact(GooTypes.BLAZE));
        assertEquals(GooFraction.of(25), result.exact(GooTypes.METAL));
    }

    /**
     * Scaling rounds to nearest integer.
     */
    @Test
    void scaleRoundsToNearest() {
        GooValue val = goo(GooTypes.BLAZE, 7);
        GooValue result = val.scale(0.5);
        assertEquals(GooFraction.of(4), result.exact(GooTypes.BLAZE)); // round(3.5) = 4
    }

    /**
     * Scaling by zero returns EMPTY.
     */
    @Test
    void scaleByZeroReturnsEmpty() {
        GooValue val = goo(GooTypes.JELLY, 100);
        GooValue result = val.scale(0.0);
        assertTrue(result.isEmpty());
    }

    /**
     * Scaling by 1.0 returns same instance.
     */
    @Test
    void scaleByOneReturnsSame() {
        GooValue val = goo(GooTypes.LEAF, 42);
        GooValue result = val.scale(1.0);
        assertSame(val, result);
    }

    /**
     * Scaling can zero out small types (excluded from result).
     */
    @Test
    void scaleCanZeroOutSmallType() {
        GooValue val = goo(GooTypes.METAL, 1);
        GooValue result = val.scale(0.3);
        assertTrue(result.isEmpty()); // round(0.3) = 0
    }

    // ── hasNegative ──────────────────────────────────────────────────────

    /**
     * A value with all positive types has no negatives.
     */
    @Test
    void allPositiveHasNoNegative() {
        GooValue val = goo(GooTypes.METAL, 10, GooTypes.CRYSTAL, 5);
        assertFalse(val.hasNegative());
    }

    /**
     * A value with a negative type reports hasNegative.
     */
    @Test
    void negativeTypeDetected() {
        Map<ResourceKey<GooTypeDefinition>, Integer> map = new HashMap<>();
        map.put(GooTypes.YORE, 32);
        map.put(GooTypes.METAL, -64);
        GooValue val = new GooValue(map);
        assertTrue(val.hasNegative());
    }

    /**
     * EMPTY has no negatives.
     */
    @Test
    void emptyHasNoNegative() {
        assertFalse(GooValue.EMPTY.hasNegative());
    }

    // ── isEmpty ─────────────────────────────────────────────────────────

    /**
     * A value with at least one positive type is not empty.
     */
    @Test
    void nonEmptyValue() {
        GooValue val = goo(GooTypes.HEX, 1);
        assertFalse(val.isEmpty());
    }

    // ── toString ────────────────────────────────────────────────────────

    /**
     * Empty value toString returns "none".
     */
    @Test
    void toStringEmpty() {
        assertEquals("none", GooValue.EMPTY.toString());
    }

    /**
     * Non-empty toString contains the type id and amount.
     */
    @Test
    void toStringContainsTypeAndAmount() {
        GooValue val = goo(GooTypes.JELLY, 5);
        String s = val.toString();
        assertTrue(s.contains("jelly"));
        assertTrue(s.contains("5"));
    }
}
