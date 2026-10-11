package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.client.PrismCrystal;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Relay and Reflector prisms rest on the four-sided column and the
 * Metronome on the six-sided one, and the fold from six sides ends on a square
 * (decision relay-and-metronome-read-apart-at-rest).
 */
class PrismComboStyleTest {

    private static final double EPSILON = 1e-6;

    @Test
    void relayAndReflectorRestFourSidedAndMetronomeSix() {
        assertEquals(PrismCrystal.ColumnSides.FOUR, PulsePrismStyle.RELAY.restingSides());
        assertEquals(PrismCrystal.ColumnSides.FOUR, new GlowReflectorStyle().restingSides());
        assertEquals(PrismCrystal.ColumnSides.SIX, PulsePrismStyle.METRONOME.restingSides());
    }

    @Test
    void theFoldEndsOnASquareColumn() {
        double radius = PrismCrystal.PRISMS.getFirst().radius();
        for (Vec3 corner : corners(PrismCrystal.foldFaces(1))) {
            assertTrue(squareReach(corner) <= radius + EPSILON, "a corner off the square at " + corner);
        }
    }

    @Test
    void theFoldStartsOnTheSixSidedColumn() {
        double radius = PrismCrystal.PRISMS.getFirst().radius();
        assertTrue(corners(PrismCrystal.foldFaces(0)).stream().anyMatch(corner -> squareReach(corner) > radius + EPSILON));
    }

    /** A corner's reach on the square whose corners stand on the axes: the sum of its offsets across the column. */
    private static double squareReach(Vec3 corner) {
        return Math.abs(corner.x - CrystalCluster.BASE_X) + Math.abs(corner.z - CrystalCluster.BASE_Z);
    }

    private static List<Vec3> corners(List<Vec3[]> faces) {
        return faces.stream().flatMap(Arrays::stream).toList();
    }
}
