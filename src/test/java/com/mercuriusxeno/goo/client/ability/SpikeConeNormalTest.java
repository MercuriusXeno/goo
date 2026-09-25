package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.throwing.BlobFlightRenderer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The metal spike cone shades each face by the same mid-angle normal the
 * metal dart does for the same basis and segment (decision
 * diagnose-then-fix-render-math).
 */
class SpikeConeNormalTest {

    private static final int CONE_SIDES = 3;
    private static final float TWO_PI = (float) (2 * Math.PI);
    private static final float TOLERANCE = 1e-5f;

    private static final float[][] DIRECTIONS = {
            {1f, 0f, 0f},
            {0f, 1f, 0f},
            {0f, -1f, 0f},
            {0f, 0f, 1f},
            {0.6f, 0.48f, 0.64f},
            {-0.36f, -0.48f, 0.8f},
    };

    static Stream<Arguments> directionAndSegment() {
        return Stream.of(DIRECTIONS).flatMap(dir ->
                IntStream.rangeClosed(0, CONE_SIDES).mapToObj(segment -> Arguments.of(dir, segment)));
    }

    @ParameterizedTest
    @MethodSource("directionAndSegment")
    void coneSegmentNormal(float[] dir, int segment) {
        float[] basis = ConeGeometry.computeBasis(dir[0], dir[1], dir[2]);
        float a0 = TWO_PI * segment / CONE_SIDES;
        float a1 = TWO_PI * (segment + 1) / CONE_SIDES;
        float[] dartNormal = BlobFlightRenderer.segmentNormal(basis, (a0 + a1) / 2f);

        assertArrayEquals(dartNormal, MetalSpikeVisual.coneSegmentNormal(basis, segment), TOLERANCE);
    }
}
