package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Goo explosion's ray march over fake resistance lookups: the
 * max-reach cap, the absence of per-ray variance, and the diminished
 * resistance a ray pays.
 */
class ExplosionMarchTest {

    private static final Vec3 CENTER = new Vec3(0.5, 0.5, 0.5);
    private static final BlockPos ORIGIN = BlockPos.containing(CENTER);
    private static final ExplosionMarch.ResistanceLookup AIR = pos -> Optional.empty();
    private static final float STONE = 6.0F;
    private static final float OBSIDIAN = 1200.0F;
    /** A resistance whose diminished value is stone's raw 6.0, so a ray pays vanilla's toll for stone. */
    private static final float VANILLA_STONE_TOLL = 49.0F;
    private static final float DIG_POWER = 4.0F;
    private static final float FULL_REACH_POWER = 2.25F;
    private static final int FULL_REACH_CELLS = 3;
    private static final double TOLERANCE = 1e-6;

    private static ExplosionMarch.ResistanceLookup solid(float resistance) {
        return pos -> Optional.of(resistance);
    }

    private static Set<BlockPos> march(float power, ExplosionMarch.ResistanceLookup lookup) {
        return ExplosionMarch.markedCells(CENTER, power, lookup);
    }

    @Nested
    class MaxReach {

        @ParameterizedTest
        @ValueSource(floats = {2.25F, 6.0F})
        void noMarkedCellLiesOutsideTheReachSphere(float power) {
            double reach = ExplosionMarch.maxReach(power);
            Set<BlockPos> marked = march(power, AIR);
            for (BlockPos cell : marked) {
                assertTrue(Vec3.atCenterOf(cell).distanceTo(CENTER) <= reach, cell.toShortString());
            }
            int pastReach = (int) Math.floor(reach) + 1;
            for (Direction direction : Direction.values()) {
                assertFalse(marked.contains(ORIGIN.relative(direction, pastReach)), direction.getName());
            }
        }

        @Test
        void everyAxisRayReachesTheFullReachInOpenAir() {
            assertEquals(FULL_REACH_CELLS, ExplosionMarch.maxReach(FULL_REACH_POWER), TOLERANCE);
            Set<BlockPos> marked = march(FULL_REACH_POWER, AIR);
            for (Direction direction : Direction.values()) {
                assertTrue(marked.contains(ORIGIN.relative(direction, FULL_REACH_CELLS)), direction.getName());
            }
        }
    }

    @Nested
    class DiminishedResistance {

        @Test
        void weakBlocksDiminishToNothing() {
            assertEquals(0.0F, ExplosionMarch.diminishedResistance(0.5F));
        }

        @Test
        void strongBlocksDiminishToTheirRootLessOne() {
            assertEquals(Math.sqrt(STONE) - 1, ExplosionMarch.diminishedResistance(STONE), TOLERANCE);
            assertEquals(Math.sqrt(OBSIDIAN) - 1, ExplosionMarch.diminishedResistance(OBSIDIAN), 1e-4);
        }

        @Test
        void rayCutsTwoStoneCellsDownWhereVanillasTollCutsOne() {
            Set<BlockPos> goo = march(DIG_POWER, solid(STONE));
            assertTrue(goo.contains(ORIGIN));
            assertTrue(goo.contains(ORIGIN.below()));
            assertFalse(goo.contains(ORIGIN.below(2)));

            Set<BlockPos> vanillaToll = march(DIG_POWER, solid(VANILLA_STONE_TOLL));
            assertTrue(vanillaToll.contains(ORIGIN));
            assertFalse(vanillaToll.contains(ORIGIN.below()));
        }

        @Test
        void obsidianHoldsEveryRay() {
            assertTrue(march(DIG_POWER, solid(OBSIDIAN)).isEmpty());
        }
    }
}
