package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
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
        assertEquals(0, val.get(GooTypes.METAL));
        assertEquals(-5, val.get(GooTypes.CRYSTAL));
        assertEquals(10, val.get(GooTypes.LEAF));
    }

    /**
     * Empty map produces an empty GooValue.
     */
    @Test
    void emptyMapProducesEmptyValue() {
        GooValue val = new GooValue(Map.of());
        assertTrue(val.isEmpty());
        assertEquals(0, val.totalBlobs());
    }

    /**
     * EMPTY singleton is truly empty.
     */
    @Test
    void emptySingletonIsEmpty() {
        assertTrue(GooValue.EMPTY.isEmpty());
        assertEquals(0, GooValue.EMPTY.totalBlobs());
        assertNull(GooValue.EMPTY.largestType());
    }

    // ── totalBlobs ──────────────────────────────────────────────────────

    /**
     * Total blobs sums all types.
     */
    @Test
    void totalBlobsSumsAllTypes() {
        GooValue val = goo(GooTypes.METAL, 5, GooTypes.CRYSTAL, 3);
        assertEquals(8, val.totalBlobs());
    }

    /**
     * Single type total blobs equals that type's amount.
     */
    @Test
    void singleTypeTotalBlobs() {
        GooValue val = goo(GooTypes.VITAL, 42);
        assertEquals(42, val.totalBlobs());
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
        assertEquals(8, result.get(GooTypes.METAL));
    }

    /**
     * Adding with multiplier scales the added value.
     */
    @Test
    void addWithMultiplier() {
        GooValue a = goo(GooTypes.ROCK, 2);
        GooValue b = goo(GooTypes.ROCK, 3);
        GooValue result = a.add(b, 4);
        assertEquals(14, result.get(GooTypes.ROCK)); // 2 + 3*4
    }

    /**
     * Adding introduces new types.
     */
    @Test
    void addIntroducesNewType() {
        GooValue a = goo(GooTypes.METAL, 5);
        GooValue b = goo(GooTypes.CRYSTAL, 3);
        GooValue result = a.add(b, 1);
        assertEquals(5, result.get(GooTypes.METAL));
        assertEquals(3, result.get(GooTypes.CRYSTAL));
    }

    /**
     * Adding to EMPTY gives the added value.
     */
    @Test
    void addToEmpty() {
        GooValue b = goo(GooTypes.GLOW, 7);
        GooValue result = GooValue.EMPTY.add(b, 1);
        assertEquals(7, result.get(GooTypes.GLOW));
    }

    /**
     * Adding a value with negatives subtracts those types (exposed copper use case).
     */
    @Test
    void addWithNegativeSubtractsType() {
        GooValue copper = goo(GooTypes.METAL, 100, GooTypes.ROCK, 20);
        Map<ResourceKey<GooTypeDefinition>, Integer> exposedMap = new HashMap<>();
        exposedMap.put(GooTypes.AEON, 32);
        exposedMap.put(GooTypes.METAL, -64);
        GooValue exposed = new GooValue(exposedMap);

        GooValue result = copper.add(exposed, 1);
        assertEquals(36, result.get(GooTypes.METAL));  // 100 - 64
        assertEquals(32, result.get(GooTypes.AEON));    // 0 + 32
        assertEquals(20, result.get(GooTypes.ROCK));    // unchanged
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
        assertEquals(-40, result.get(GooTypes.METAL));
    }

    /**
     * floorZero clamps all negative types to zero.
     */
    @Test
    void floorZeroClampsNegatives() {
        Map<ResourceKey<GooTypeDefinition>, Integer> map = new HashMap<>();
        map.put(GooTypes.METAL, -40);
        map.put(GooTypes.AEON, 32);
        map.put(GooTypes.ROCK, 0);
        GooValue val = new GooValue(map);

        GooValue floored = val.floorZero();
        assertEquals(0, floored.get(GooTypes.METAL));
        assertEquals(32, floored.get(GooTypes.AEON));
        assertTrue(floored.getAll().containsKey(GooTypes.AEON));
        assertFalse(floored.getAll().containsKey(GooTypes.METAL));
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
        assertEquals(7, result.get(GooTypes.METAL));
        assertEquals(6, result.get(GooTypes.CRYSTAL));
    }

    /**
     * Subtracting more than available goes negative.
     */
    @Test
    void subtractCanGoNegative() {
        GooValue a = goo(GooTypes.METAL, 5);
        GooValue b = goo(GooTypes.METAL, 10);
        GooValue result = a.subtract(b);
        assertEquals(-5, result.get(GooTypes.METAL));
    }

    /**
     * Subtracting from EMPTY produces negative values.
     */
    @Test
    void subtractFromEmptyGoesNegative() {
        GooValue b = goo(GooTypes.LEAF, 5);
        GooValue result = GooValue.EMPTY.subtract(b);
        assertEquals(-5, result.get(GooTypes.LEAF));
    }

    /**
     * Subtracting a type not present in the source introduces a negative.
     */
    @Test
    void subtractMissingTypeGoesNegative() {
        GooValue a = goo(GooTypes.METAL, 10);
        GooValue b = goo(GooTypes.CRYSTAL, 5);
        GooValue result = a.subtract(b);
        assertEquals(10, result.get(GooTypes.METAL));
        assertEquals(-5, result.get(GooTypes.CRYSTAL));
    }

    // ── multiply ────────────────────────────────────────────────────────

    /**
     * Multiplying scales all types.
     */
    @Test
    void multiplyScalesAllTypes() {
        GooValue val = goo(GooTypes.METAL, 10, GooTypes.CRYSTAL, 6);
        GooValue result = val.multiply(3);
        assertEquals(30, result.get(GooTypes.METAL));
        assertEquals(18, result.get(GooTypes.CRYSTAL));
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
        GooValue val = goo(GooTypes.VITAL, 100);
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

    // ── divideExact ──────────────────────────────────────────────────────

    /**
     * Exact division with clean divisor succeeds.
     */
    @Test
    void divideExactClean() {
        GooValue val = goo(GooTypes.METAL, 18, GooTypes.CRYSTAL, 9);
        GooValue result = val.divideExact(9);
        assertEquals(2, result.get(GooTypes.METAL));
        assertEquals(1, result.get(GooTypes.CRYSTAL));
    }

    /**
     * Exact division with remainder throws ArithmeticException.
     */
    @Test
    void divideExactLossyThrows() {
        GooValue val = goo(GooTypes.METAL, 10);
        assertThrows(ArithmeticException.class, () -> val.divideExact(3));
    }

    /**
     * Exact division by 1 returns same instance.
     */
    @Test
    void divideExactByOneReturnsSame() {
        GooValue val = goo(GooTypes.BLAZE, 15);
        assertSame(val, val.divideExact(1));
    }

    // ── divide ──────────────────────────────────────────────────────────

    /**
     * Dividing evenly produces exact result.
     */
    @Test
    void divideEvenly() {
        GooValue val = goo(GooTypes.METAL, 10);
        GooValue result = val.divide(2);
        assertEquals(5, result.get(GooTypes.METAL));
    }

    /**
     * Dividing with remainder floors the result.
     */
    @Test
    void divideWithRemainder() {
        GooValue val = goo(GooTypes.METAL, 7);
        GooValue result = val.divide(3);
        assertEquals(2, result.get(GooTypes.METAL)); // 7/3 = 2
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
     * Dividing can zero out a type (excluded from result).
     */
    @Test
    void divideCanZeroOutType() {
        GooValue val = goo(GooTypes.METAL, 1);
        GooValue result = val.divide(2);
        assertTrue(result.isEmpty()); // 1/2 = 0 → empty
    }

    /**
     * Multi-type divide divides each type independently.
     */
    @Test
    void multiTypeDivide() {
        GooValue val = goo(GooTypes.METAL, 10, GooTypes.CRYSTAL, 6);
        GooValue result = val.divide(3);
        assertEquals(3, result.get(GooTypes.METAL)); // 10/3
        assertEquals(2, result.get(GooTypes.CRYSTAL)); // 6/3
    }

    // ── scale ──────────────────────────────────────────────────────────

    /**
     * Scaling by 0.5 halves all types (rounded).
     */
    @Test
    void scaleByHalf() {
        GooValue val = goo(GooTypes.BLAZE, 100, GooTypes.METAL, 50);
        GooValue result = val.scale(0.5);
        assertEquals(50, result.get(GooTypes.BLAZE));
        assertEquals(25, result.get(GooTypes.METAL));
    }

    /**
     * Scaling rounds to nearest integer.
     */
    @Test
    void scaleRoundsToNearest() {
        GooValue val = goo(GooTypes.BLAZE, 7);
        GooValue result = val.scale(0.5);
        assertEquals(4, result.get(GooTypes.BLAZE)); // round(3.5) = 4
    }

    /**
     * Scaling by zero returns EMPTY.
     */
    @Test
    void scaleByZeroReturnsEmpty() {
        GooValue val = goo(GooTypes.VITAL, 100);
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
        map.put(GooTypes.AEON, 32);
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
        GooValue val = goo(GooTypes.VITAL, 5);
        String s = val.toString();
        assertTrue(s.contains("vital"));
        assertTrue(s.contains("5"));
    }
}
