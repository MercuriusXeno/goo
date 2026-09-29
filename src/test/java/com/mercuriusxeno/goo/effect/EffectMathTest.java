package com.mercuriusxeno.goo.effect;

import com.mercuriusxeno.goo.ability.AbilityMath;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AbilityMath pure functions: range formulas for chain effects.
 */
class EffectMathTest {

    // ── Rock: isRockCompatible (majority rule) ────────────────────────────

    @Nested
    class RockCompatible {

        @Test
        void rockOnlyIsCompatible() {
            GooValue value = new GooValue(Map.of(GooTypes.ROCK, 3));
            assertTrue(AbilityMath.isRockCompatible(value));
        }

        @Test
        void rockAndCrystalIsCompatible() {
            GooValue value = new GooValue(Map.of(GooTypes.ROCK, 2, GooTypes.CRYSTAL, 1));
            assertTrue(AbilityMath.isRockCompatible(value));
        }

        @Test
        void crystalOnlyIsCompatible() {
            GooValue value = new GooValue(Map.of(GooTypes.CRYSTAL, 2));
            assertTrue(AbilityMath.isRockCompatible(value));
        }

        @Test
        void rockMajorityWithMinorityMetalIsCompatible() {
            GooValue value = new GooValue(Map.of(GooTypes.ROCK, 3, GooTypes.METAL, 1));
            assertTrue(AbilityMath.isRockCompatible(value));
        }

        @Test
        void rockMinorityIsNotCompatible() {
            GooValue value = new GooValue(Map.of(GooTypes.ROCK, 1, GooTypes.METAL, 3));
            assertFalse(AbilityMath.isRockCompatible(value));
        }

        @Test
        void exactHalfIsNotCompatible() {
            GooValue value = new GooValue(Map.of(GooTypes.ROCK, 2, GooTypes.METAL, 2));
            assertFalse(AbilityMath.isRockCompatible(value));
        }

        @Test
        void emptyIsNotCompatible() {
            assertFalse(AbilityMath.isRockCompatible(GooValue.EMPTY));
        }

        @Test
        void nullIsNotCompatible() {
            assertFalse(AbilityMath.isRockCompatible(null));
        }
    }

}
