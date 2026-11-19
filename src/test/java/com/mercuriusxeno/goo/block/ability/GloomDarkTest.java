package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that a spot on Gloom passes the light part of a hostile spawn check
 * at any light, and that every other spot keeps vanilla's answer, which in
 * the overworld passes only at light 0; that Gloom's own cell leaves a
 * spawning mob's box free; and that a thing on Gloom is out of the daylight.
 */
class GloomDarkTest {

    private static final BlockPos SPOT = new BlockPos(4, 64, -7);
    private static final int FULL_LIGHT = 15;
    private static final int NO_LIGHT = 0;
    private static final Predicate<BlockPos> GLOOM_AT_THE_FEET = SPOT::equals;
    private static final Predicate<BlockPos> GLOOM_UNDER_THE_FEET = SPOT.below()::equals;
    private static final Predicate<BlockPos> NO_GLOOM = pos -> false;
    /** Vanilla's overworld monster walk target value at full light, a monster shunning the lit spot. */
    private static final float LIT_WALK_TARGET_VALUE = -0.5f;
    /** Vanilla's value at no light, a monster drawn to the dark spot. */
    private static final float DARK_WALK_TARGET_VALUE = 0.5f;

    /**
     * Vanilla's overworld light check: no block light is allowed, so a spot
     * passes only where it stands at light 0.
     */
    private static boolean overworldPasses(int light) {
        return light == NO_LIGHT;
    }

    @Nested
    class SpawnLight {

        @Test
        void aSpotOnGloomPassesAtFullLight() {
            assertTrue(GloomDark.spawnLightPasses(SPOT, GLOOM_AT_THE_FEET, overworldPasses(FULL_LIGHT)));
        }

        @Test
        void aSpotOverGloomPassesAtFullLight() {
            assertTrue(GloomDark.spawnLightPasses(SPOT, GLOOM_UNDER_THE_FEET, overworldPasses(FULL_LIGHT)));
        }

        @Test
        void aSpotOffGloomFailsAtFullLight() {
            assertFalse(GloomDark.spawnLightPasses(SPOT, NO_GLOOM, overworldPasses(FULL_LIGHT)));
        }

        @Test
        void aSpotOffGloomPassesAtNoLight() {
            assertTrue(GloomDark.spawnLightPasses(SPOT, NO_GLOOM, overworldPasses(NO_LIGHT)));
        }
    }

    /**
     * Operator ruling 2026-11-18, "Goo is no obstruction": Gloom's own cell
     * does not count against a spawning mob's box, and every other collision does.
     */
    @Nested
    class SpawnSpace {

        @Test
        void aBoxCaughtOnlyByGloomIsFree() {
            assertTrue(GloomDark.spawnSpaceIsFree(SPOT, GLOOM_AT_THE_FEET, false, () -> true));
        }

        @Test
        void aBoxCaughtByMoreThanGloomStaysObstructed() {
            assertFalse(GloomDark.spawnSpaceIsFree(SPOT, GLOOM_AT_THE_FEET, false, () -> false));
        }

        @Test
        void aBoxOffGloomKeepsVanillasAnswer() {
            assertFalse(GloomDark.spawnSpaceIsFree(SPOT, NO_GLOOM, false, () -> true));
        }
    }

    @Nested
    class Daylight {

        /** The light value a mob reads under the open noon sky, the brightest. */
        private static final float NOON_VALUE = 1f;
        /** The light value the overworld reads at no light. */
        private static final float DARK_VALUE = 0f;

        @Test
        void aThingOnGloomIsOutOfTheDaylight() {
            assertFalse(GloomDark.inDaylight(SPOT, GLOOM_AT_THE_FEET, true));
        }

        @Test
        void aThingOverGloomIsOutOfTheDaylight() {
            assertFalse(GloomDark.inDaylight(SPOT, GLOOM_UNDER_THE_FEET, true));
        }

        @Test
        void aThingOffGloomStaysInTheDaylight() {
            assertTrue(GloomDark.inDaylight(SPOT, NO_GLOOM, true));
        }

        @Test
        void aMobOnGloomReadsTheDarkUnderTheNoonSky() {
            assertEquals(DARK_VALUE, GloomDark.daylightValue(SPOT, GLOOM_AT_THE_FEET, NOON_VALUE, DARK_VALUE));
        }

        @Test
        void aMobOffGloomReadsTheNoonSky() {
            assertEquals(NOON_VALUE, GloomDark.daylightValue(SPOT, NO_GLOOM, NOON_VALUE, DARK_VALUE));
        }
    }

    @Nested
    class SpawnWalkTarget {

        @Test
        void aLitSpotOnGloomReadsAsNoLongerShunned() {
            assertTrue(GloomDark.spawnWalkTargetValue(SPOT, GLOOM_AT_THE_FEET, LIT_WALK_TARGET_VALUE) >= 0f);
        }

        @Test
        void aLitSpotOffGloomKeepsVanillasValue() {
            assertEquals(LIT_WALK_TARGET_VALUE,
                    GloomDark.spawnWalkTargetValue(SPOT, NO_GLOOM, LIT_WALK_TARGET_VALUE));
        }

        @Test
        void aDarkSpotOnGloomKeepsVanillasValue() {
            assertEquals(DARK_WALK_TARGET_VALUE,
                    GloomDark.spawnWalkTargetValue(SPOT, GLOOM_AT_THE_FEET, DARK_WALK_TARGET_VALUE));
        }
    }
}
