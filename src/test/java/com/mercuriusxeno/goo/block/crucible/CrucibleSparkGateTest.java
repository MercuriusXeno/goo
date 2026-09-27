package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the flint and steel gate: a spark lights only a crucible holding no heat ticks and
 * no fuel goo (decision spark-gate-consumes-the-click). Grades are built from GooConfig's
 * defaults, since the unit suite loads no config.
 */
class CrucibleSparkGateTest {

    private static final List<FuelGrade> GRADES = List.of(
        new FuelGrade(GooTypes.UNSTABLE, GooConfig.DEFAULT_UNSTABLE_TICKS_PER_MB, GooConfig.DEFAULT_UNSTABLE_MELT_EXPONENT),
        new FuelGrade(GooTypes.BLAZE, GooConfig.DEFAULT_BLAZE_TICKS_PER_MB, GooConfig.DEFAULT_BLAZE_MELT_EXPONENT));
    private static final int SOME_HEAT = 20;
    private static final int SOME_FUEL = 1;

    private static boolean sparkLights(int heatTicks, ResourceKey<GooTypeDefinition> stocked) {
        CrucibleHeat heat = new CrucibleHeat();
        heat.set(heatTicks, heatTicks > 0 ? GRADES.get(1) : null);
        CrucibleHeat.FuelStock stock = new CrucibleHeat.FuelStock() {
            @Override
            public int volume(ResourceKey<GooTypeDefinition> type) {
                return type.equals(stocked) ? SOME_FUEL : 0;
            }

            @Override
            public int extract(ResourceKey<GooTypeDefinition> type, int amount) {
                return 0;
            }
        };
        return CrucibleInteraction.sparkLights(heat, GRADES, stock);
    }

    @Test
    void sparkLightsColdCrucibleWithNoFuel() {
        assertTrue(sparkLights(0, GooTypes.ROCK));
    }

    @Test
    void sparkRefusedWithHeatTicksAndNoFuel() {
        assertFalse(sparkLights(SOME_HEAT, GooTypes.ROCK));
    }

    @Test
    void sparkRefusedWithBlazeGooAndNoHeat() {
        assertFalse(sparkLights(0, GooTypes.BLAZE));
    }

    @Test
    void sparkRefusedWithUnstableGooAndNoHeat() {
        assertFalse(sparkLights(0, GooTypes.UNSTABLE));
    }

    @Test
    void sparkRefusedWithHeatTicksAndFuel() {
        assertFalse(sparkLights(SOME_HEAT, GooTypes.BLAZE));
    }
}
