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
    void bubbleSpawnsStayInsideTheFootprintOverASeededRun() {
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < 10_000; i++) {
            double coordinate = CrucibleParticleHelper.randomInBasin(random);
            assertTrue(coordinate >= CrucibleBasin.FOOTPRINT_MIN && coordinate <= CrucibleBasin.FOOTPRINT_MAX,
                "bubble coordinate " + coordinate + " left the footprint");
        }
    }

    @Test
    void bubbleSpawnsReachBothFootprintEdges() {
        RandomSource low = mock(RandomSource.class);
        when(low.nextDouble()).thenReturn(0.0);
        RandomSource high = mock(RandomSource.class);
        when(high.nextDouble()).thenReturn(Math.nextDown(1.0));
        assertEquals(CrucibleBasin.FOOTPRINT_MIN, CrucibleParticleHelper.randomInBasin(low), EPSILON);
        assertEquals(CrucibleBasin.FOOTPRINT_MAX, CrucibleParticleHelper.randomInBasin(high), EPSILON);
    }
}
