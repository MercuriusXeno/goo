package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.UpdraftStep;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Updraft's rising wind: an Updraft cell's column stands a block tall for
 * each of its layers and as wide as the shipped updraft step names, a column
 * blows once a tick however many frames draw it, and its lines leave the
 * floor inside its width and rise straight up through its height.
 * updraft-blob-stands-a-column-of-wind
 * updraft-goo-lifts-one-block-per-layer
 */
class UpdraftWindTest {

    private static final double EPSILON = 1e-9;
    private static final Vec3 BASE = new Vec3(2.5, 1, 2.5);

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 15})
    void aCellsColumnIsAsTallAsItsLayersAndAsWideAsTheStepNames(int layers) {
        UpdraftStep shipped = UpdraftStep.in(AbilityJson.decode("typhoon_updraft").behaviors()).orElseThrow();

        UpdraftWind.Column column = UpdraftWind.columnOf(shipped, layers);

        assertEquals(layers, column.height(), EPSILON);
        assertEquals(shipped.halfWidth(), column.radius(), EPSILON);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 7L, 1000L})
    void aCellBlowsOneLineInEverySixTicks(long from) {
        for (BlockPos cell : new BlockPos[] {new BlockPos(0, 64, 0), new BlockPos(3, 70, -9), new BlockPos(-41, 5, 12)}) {
            int blown = 0;
            for (long tick = from; tick < from + UpdraftWind.WIND_EVERY_TICKS; tick++) {
                blown += UpdraftWind.blowsOnCadence(cell, tick) ? 1 : 0;
            }

            assertEquals(1, blown, cell + " from " + from);
        }
    }

    @Test
    void aColumnBlowsOnceATickHoweverManyFramesSeeIt() {
        BlockPos pos = new BlockPos(40, 70, -12);

        assertTrue(UpdraftWind.blowsNow(pos, 100L));
        assertFalse(UpdraftWind.blowsNow(pos, 100L));
        assertTrue(UpdraftWind.blowsNow(pos, 101L));
        UpdraftWind.clear();
    }

    @Test
    void aRisingLineLeavesTheFloorInsideTheWidthAndRisesTheColumnsHeight() {
        SprayCone gust = WindLines.riseGust(BASE, 1, 8, 1, -1);

        assertEquals(BASE.x + WindLines.RISE_INSET, gust.apex().x, EPSILON);
        assertEquals(BASE.z - WindLines.RISE_INSET, gust.apex().z, EPSILON);
        assertEquals(BASE.y, gust.apex().y, EPSILON);
        assertEquals(new Vec3(0, 1, 0), gust.axis());
        assertEquals(8, gust.reach(), EPSILON);
    }
}
