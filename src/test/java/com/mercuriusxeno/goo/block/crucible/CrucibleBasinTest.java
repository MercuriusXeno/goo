package com.mercuriusxeno.goo.block.crucible;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that the crucible basin seam answers the goocible model's eight-pixel
 * cavity, and that the server's surface height and bubble placement read it.
 */
class CrucibleBasinTest {

    private static final float EPSILON = 1e-6f;
    private static final int FULL_VOLUME = CrucibleBasin.RIM_VOLUME;

    @Test
    void basinFloorIsTheCavityBottom() {
        assertEquals(8f / 16f, CrucibleBasin.FLOOR_Y, EPSILON);
    }

    @Test
    void basinRimIsTheBlockTop() {
        assertEquals(16f / 16f, CrucibleBasin.RIM_Y, EPSILON);
    }

    @Test
    void footprintSpansTheCavityWalls() {
        assertEquals(4f / 16f, CrucibleBasin.FOOTPRINT_MIN, EPSILON);
        assertEquals(12f / 16f, CrucibleBasin.FOOTPRINT_MAX, EPSILON);
        assertEquals(8f / 16f, CrucibleBasin.footprintWidth(), EPSILON);
    }

    @Test
    void emptyFillSitsOnTheFloor() {
        assertEquals(CrucibleBasin.FLOOR_Y, CrucibleBasin.surfaceY(0f), EPSILON);
    }

    @Test
    void fullFillReachesTheRim() {
        assertEquals(CrucibleBasin.RIM_Y, CrucibleBasin.surfaceY(1f), EPSILON);
    }

    @Test
    void halfFillSitsHalfwayUpTheBasin() {
        assertEquals(12f / 16f, CrucibleBasin.surfaceY(0.5f), EPSILON);
    }

    @Test
    void fillOutsideTheUnitRangeClampsToTheBasin() {
        assertEquals(CrucibleBasin.FLOOR_Y, CrucibleBasin.surfaceY(-0.5f), EPSILON);
        assertEquals(CrucibleBasin.RIM_Y, CrucibleBasin.surfaceY(1.5f), EPSILON);
    }

    @Test
    void serverSurfaceOfAnEmptyCrucibleSitsOnTheFloor() {
        assertEquals(CrucibleBasin.FLOOR_Y, CrucibleParticleHelper.computeSurfaceY(0), EPSILON);
    }

    @Test
    void serverSurfaceOfAFullCrucibleReachesTheRim() {
        assertEquals(CrucibleBasin.RIM_Y, CrucibleParticleHelper.computeSurfaceY(FULL_VOLUME), EPSILON);
    }

    @Test
    void bubbleWallInsetIsOneAndAHalfPixels() {
        assertEquals(1.5 / 16.0, CrucibleParticleHelper.BUBBLE_WALL_INSET, EPSILON);
    }

    @Test
    void bubbleSpawnsStayInsetFromTheWallsOverASeededRun() {
        RandomSource random = RandomSource.create(42L);
        double low = CrucibleBasin.FOOTPRINT_MIN + CrucibleParticleHelper.BUBBLE_WALL_INSET;
        double high = CrucibleBasin.FOOTPRINT_MAX - CrucibleParticleHelper.BUBBLE_WALL_INSET;
        for (int i = 0; i < 10_000; i++) {
            double coordinate = CrucibleParticleHelper.randomInBasin(random);
            assertTrue(coordinate >= low && coordinate <= high,
                "bubble coordinate " + coordinate + " came within the wall inset");
        }
    }

    @Test
    void bubbleSpawnsReachBothInsetEdges() {
        RandomSource low = mock(RandomSource.class);
        when(low.nextDouble()).thenReturn(0.0);
        RandomSource high = mock(RandomSource.class);
        when(high.nextDouble()).thenReturn(Math.nextDown(1.0));
        assertEquals(5.5 / 16.0, CrucibleParticleHelper.randomInBasin(low), EPSILON);
        assertEquals(10.5 / 16.0, CrucibleParticleHelper.randomInBasin(high), EPSILON);
    }

    @Test
    void insetAppliesToWhateverFootprintIsGiven() {
        RandomSource low = mock(RandomSource.class);
        when(low.nextDouble()).thenReturn(0.0);
        RandomSource high = mock(RandomSource.class);
        when(high.nextDouble()).thenReturn(Math.nextDown(1.0));
        double min = 6.0 / 16.0;
        double max = 10.0 / 16.0;
        assertEquals(min + CrucibleParticleHelper.BUBBLE_WALL_INSET,
            CrucibleParticleHelper.randomInsetWithin(low, min, max), EPSILON);
        assertEquals(max - CrucibleParticleHelper.BUBBLE_WALL_INSET,
            CrucibleParticleHelper.randomInsetWithin(high, min, max), EPSILON);
    }

    @Test
    void oneBubbleSpawnsOnlyOnTheOneTickInTwenty() {
        assertEquals(20, CrucibleParticleHelper.BUBBLE_ONE_IN_TICKS);
        RandomSource random = mock(RandomSource.class);
        for (int roll = 0; roll < CrucibleParticleHelper.BUBBLE_ONE_IN_TICKS; roll++) {
            when(random.nextInt(CrucibleParticleHelper.BUBBLE_ONE_IN_TICKS)).thenReturn(roll);
            assertEquals(roll == 0 ? 1 : 0, CrucibleParticleHelper.bubbleCount(random),
                "bubble count for roll " + roll);
        }
    }

    @Test
    void bubblesAverageOneEveryTwentyTicksOverASeededRun() {
        RandomSource random = RandomSource.create(42L);
        int ticks = 200_000;
        int bubbles = 0;
        for (int i = 0; i < ticks; i++) {
            bubbles += CrucibleParticleHelper.bubbleCount(random);
        }
        double perTick = (double) bubbles / ticks;
        assertEquals(0.05, perTick, 0.005, "bubbles per tick over a seeded run");
    }
}
