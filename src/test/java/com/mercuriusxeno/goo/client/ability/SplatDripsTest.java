package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.SplittableRandom;
import static com.mercuriusxeno.goo.client.ability.MobCoats.COAT_DISSOLVE_TICKS;
import static com.mercuriusxeno.goo.client.ability.MobCoats.COAT_HOLD_TICKS;
import static com.mercuriusxeno.goo.client.ability.MobCoats.COAT_LIFE_TICKS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A splat sheds drips from its lower half from the impact until it is gone,
 * about three a second just after the hit, thinning to about one every two seconds.
 */
class SplatDripsTest {

    private static final Vec3 HIT = new Vec3(10.5, 65.2, -3.25);
    private static final int MIDWAY_THROUGH_DISSOLVE = COAT_HOLD_TICKS + COAT_DISSOLVE_TICKS / 2;
    private static final int TICKS_PER_SECOND = 20;
    private static final int SEEDS = 4000;
    private static final double RATE_TOLERANCE = 0.03;

    private static double meanDripsAt(int ageTicks) {
        long total = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            total += SplatDrips.dripsAt(ageTicks, new SplittableRandom(seed));
        }
        return (double) total / SEEDS;
    }

    @Nested
    class Rate {

        @Test
        void freshSplatDripsAboutThreeASecond() {
            assertEquals(3.0, SplatDrips.dripRate(0) * TICKS_PER_SECOND, 0.01);
        }

        @Test
        void agedSplatThinsToAboutOneEveryTwoSeconds() {
            assertEquals(0.5, SplatDrips.dripRate(COAT_LIFE_TICKS - 1) * TICKS_PER_SECOND, 0.05);
        }

        @Test
        void freshSplatDripsMoreThanADissolvingOne() {
            assertTrue(SplatDrips.dripRate(1) > SplatDrips.dripRate(MIDWAY_THROUGH_DISSOLVE));
            assertTrue(SplatDrips.dripRate(MIDWAY_THROUGH_DISSOLVE) > 0f);
        }

        @Test
        void tickCountsAverageToTheRate() {
            assertEquals(SplatDrips.dripRate(1), meanDripsAt(1), RATE_TOLERANCE);
            assertEquals(SplatDrips.dripRate(MIDWAY_THROUGH_DISSOLVE), meanDripsAt(MIDWAY_THROUGH_DISSOLVE),
                    RATE_TOLERANCE);
        }

        @Test
        void goneSplatDripsNothing() {
            assertEquals(0f, SplatDrips.dripRate(COAT_LIFE_TICKS));
            assertEquals(0.0, meanDripsAt(COAT_LIFE_TICKS));
        }
    }

    @Nested
    class Points {

        @Test
        void everyDripLeavesTheSplatsLowerHalf() {
            for (int seed = 0; seed < SEEDS; seed++) {
                List<Vec3> points = SplatDrips.shed(HIT, 1, new SplittableRandom(seed));
                for (Vec3 point : points) {
                    assertTrue(point.distanceTo(HIT) <= SplatDrips.SPLAT_RADIUS, "drip outside the splat: " + point);
                    assertTrue(point.y <= HIT.y, "drip above the hit point: " + point);
                }
            }
        }

        @Test
        void shedsAsManyPointsAsTheTicksDrips() {
            for (int seed = 0; seed < SEEDS; seed++) {
                assertEquals(SplatDrips.dripsAt(1, new SplittableRandom(seed)),
                        SplatDrips.shed(HIT, 1, new SplittableRandom(seed)).size());
            }
        }
    }
}
