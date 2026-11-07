package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each tendril of the vignette rolls its own shape, so no two along the
 * screen's edges look alike, and keeps it from frame to frame
 * (decisions sight-lengthens-shift-and-outlines-fungus and
 * sculker-sight-persists-where-echoes-have-touched).
 */
class TendrilVignetteTest {

    private static final int EDGES = 4;
    private static final int PER_EDGE = 5;

    private static List<TendrilVignette.Shape> everyShape() {
        List<TendrilVignette.Shape> shapes = new ArrayList<>();
        for (int edge = 0; edge < EDGES; edge++) {
            for (int index = 0; index < PER_EDGE; index++) {
                shapes.add(TendrilVignette.shapeOf(edge, index));
            }
        }
        return shapes;
    }

    @Test
    void noTwoTendrilsShareALengthOrASway() {
        List<TendrilVignette.Shape> shapes = everyShape();
        Set<Float> lengths = new HashSet<>();
        Set<Float> sways = new HashSet<>();
        shapes.forEach(shape -> {
            lengths.add(shape.length());
            sways.add(shape.sway());
        });
        assertEquals(shapes.size(), lengths.size(), "every tendril should reach its own length");
        assertEquals(shapes.size(), sways.size(), "every tendril should sway its own amount");
    }

    @Test
    void someTendrilsCurlOrBranchAndOthersDoNot() {
        List<TendrilVignette.Shape> shapes = everyShape();
        assertTrue(shapes.stream().anyMatch(shape -> shape.branchAt() >= 0)
                && shapes.stream().anyMatch(shape -> shape.branchAt() < 0), "some tendrils should branch, not all");
        assertTrue(shapes.stream().anyMatch(shape -> shape.curl() > 0)
                && shapes.stream().anyMatch(shape -> shape.curl() < 0), "tendrils should curl both ways");
    }

    @Test
    void aTendrilKeepsItsShapeFromFrameToFrame() {
        assertEquals(TendrilVignette.shapeOf(2, 3), TendrilVignette.shapeOf(2, 3));
    }
}
