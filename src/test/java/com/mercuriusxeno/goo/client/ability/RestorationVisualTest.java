package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The restoration waves' geometry: each ring stands across the cone at its
 * distance with the cone's half-width as its radius, and the waves roll
 * out along the cone, spaced evenly, wrapping at its range (decision
 * vitality-waves-regenerate-and-court).
 */
class RestorationVisualTest {

    private static final double EPSILON = 1e-4;
    private static final Vec3 APEX = new Vec3(1, 2, 3);
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    @Nested
    class RingPoints {

        @Test
        void everyPointSitsAtTheConesHalfWidthAroundTheAxis() {
            double distance = 4;
            List<Vec3> ring = RestorationVisual.ringPoints(APEX, EAST, distance, 90, 8);
            Vec3 center = APEX.add(EAST.scale(distance));
            for (Vec3 point : ring) {
                assertEquals(distance, point.distanceTo(center), EPSILON);
            }
        }

        @Test
        void everyPointStandsInThePlaneAcrossTheAxis() {
            List<Vec3> ring = RestorationVisual.ringPoints(APEX, EAST, 3, 40, 6);
            for (Vec3 point : ring) {
                assertEquals(APEX.x + 3, point.x, EPSILON);
            }
        }

        @Test
        void theRingHoldsTheCountAskedSpacedEvenly() {
            List<Vec3> ring = RestorationVisual.ringPoints(APEX, EAST, 2, 90, 4);
            assertEquals(4, ring.size());
            double side = ring.get(0).distanceTo(ring.get(1));
            assertEquals(side, ring.get(1).distanceTo(ring.get(2)), EPSILON);
            assertEquals(Math.sqrt(2) * 2, side, EPSILON);
        }
    }

    @Nested
    class WaveDistance {

        @Test
        void aWaveRollsOutAtTheWaveSpeed() {
            assertEquals(RestorationVisual.WAVE_SPEED * 10, RestorationVisual.waveDistance(10, 0, 6), EPSILON);
        }

        @Test
        void wavesSpaceEvenlyOverTheRange() {
            assertEquals(2, RestorationVisual.waveDistance(0, 1, 6), EPSILON);
        }

        @Test
        void aWaveWrapsToTheApexAtTheRange() {
            long ticksToRange = 20;
            double range = RestorationVisual.WAVE_SPEED * ticksToRange;
            assertEquals(RestorationVisual.WAVE_SPEED, RestorationVisual.waveDistance(ticksToRange + 1, 0, range),
                    EPSILON);
        }

        @Test
        void aStreamWithNoRangeHasNoWave() {
            assertEquals(0, RestorationVisual.waveDistance(10, 1, 0), EPSILON);
        }
    }
}
