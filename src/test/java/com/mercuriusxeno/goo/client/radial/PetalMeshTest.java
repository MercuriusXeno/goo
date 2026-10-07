package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the petal's edge strip as one continuous ribbon: neighboring quads
 * share their corners, so no sliver of fill shows between them where the
 * outline bends, as it does along the inner arc by the hub (decision
 * wedges-take-a-solid-edge), and the square a sprite is cut to at the
 * petal's border (decision icons-slide-in-from-behind-the-tip).
 */
class PetalMeshTest {

    private static final double ARC = 2.0 * Math.PI / 16;
    private static final double START = 3 * ARC;

    private static Set<String> corners(PetalMesh.Quad quad) {
        return quad.corners().stream().map(vertex -> Math.round(vertex.x() * 1e9) + "," + Math.round(vertex.y() * 1e9))
                .collect(Collectors.toSet());
    }

    private static void assertRibbonIsContinuous(PetalMask.Petal petal) {
        List<PetalMesh.Quad> edge = PetalMesh.edge(petal, PetalMesh.EDGE_THICKNESS, true);
        for (int i = 0; i < edge.size(); i++) {
            Set<String> shared = corners(edge.get(i));
            shared.retainAll(corners(edge.get((i + 1) % edge.size())));
            assertTrue(shared.size() >= 2, "quad " + i + " shares a side with the next");
        }
    }

    /**
     * An ability petal leaves the border along its inner side to the base it
     * starts from, so one border marks the join (decision petal-moves-animate).
     */
    @Test
    void abilityPetalDrawsNoBorderAlongItsJoinWithTheBase() {
        double base = RadialWheel.TYPE_BASE_LENGTH;
        PetalMask.Petal card = new PetalMask.Petal(START, ARC, base, 1.0);
        double midAngle = START + ARC / 2;

        boolean bordersTheJoin = PetalMesh.edge(card, PetalMesh.EDGE_THICKNESS, false).stream()
                .flatMap(quad -> quad.corners().stream())
                .anyMatch(vertex -> Math.abs(Math.hypot(vertex.x(), vertex.y()) - base) < PetalMesh.EDGE_THICKNESS / 2
                        && Math.abs(Math.IEEEremainder(RadialWheel.angleOf(vertex.x(), vertex.y()) - midAngle,
                        2 * Math.PI)) < ARC / 4);

        assertFalse(bordersTheJoin);
        assertTrue(PetalMesh.edge(card, PetalMesh.EDGE_THICKNESS, true).stream()
                .flatMap(quad -> quad.corners().stream())
                .anyMatch(vertex -> Math.abs(Math.hypot(vertex.x(), vertex.y()) - base) < 1e-9
                        && Math.abs(Math.IEEEremainder(RadialWheel.angleOf(vertex.x(), vertex.y()) - midAngle,
                        2 * Math.PI)) < ARC / 4), "the full ribbon does border it");
    }

    /**
     * Where an ability petal's border stops at the base, it ends square: the
     * last quad's inner corner sits straight across its segment, one
     * thickness in, not mitered to a point against the join it skips.
     */
    @Test
    void abilityPetalBorderEndsSquareAtTheBase() {
        PetalMask.Petal base = new PetalMask.Petal(START, 4 * ARC, RadialWheel.HUB_FRACTION,
                RadialWheel.TYPE_BASE_LENGTH);
        PetalMask.Petal card = new PetalMask.Petal(START, ARC, base.outer(), 1.0, base);
        List<PetalMesh.Quad> edge = PetalMesh.edge(card, PetalMesh.EDGE_THICKNESS, false);

        for (PetalMesh.Quad end : List.of(edge.getFirst(), edge.getLast())) {
            List<PetalMesh.Vertex> corners = end.corners();
            for (int i = 0; i < corners.size(); i++) {
                PetalMesh.Vertex a = corners.get(i);
                PetalMesh.Vertex b = corners.get((i + 1) % corners.size());
                PetalMesh.Vertex c = corners.get((i + 2) % corners.size());
                double dot = (a.x() - b.x()) * (c.x() - b.x()) + (a.y() - b.y()) * (c.y() - b.y());
                double sides = Math.hypot(a.x() - b.x(), a.y() - b.y()) * Math.hypot(c.x() - b.x(), c.y() - b.y());
                assertTrue(Math.abs(dot / sides) < 0.2, "an end quad's corner " + i + " is near square");
            }
        }
    }

    @Test
    void edgeRibbonOfAnAbilityPetalSharesEveryCorner() {
        assertRibbonIsContinuous(new PetalMask.Petal(START, ARC, RadialWheel.HUB_FRACTION, 1.0));
    }

    @Test
    void edgeRibbonOfAWideBaseSharesEveryCorner() {
        assertRibbonIsContinuous(new PetalMask.Petal(START, 4 * ARC, RadialWheel.HUB_FRACTION,
                RadialWheel.TYPE_BASE_LENGTH));
    }

    /** How far, in normalized units, a cut corner may sit off the petal: float rounding at the border. */
    static final double EDGE_SLACK = 1e-6;

    /**
     * Whether a point lies inside a petal or within {@link #EDGE_SLACK} of it,
     * so a corner cut exactly on the border reads inside.
     *
     * @param petal the petal
     * @param x     normalized x
     * @param y     normalized y
     * @return true inside or on the border
     */
    static boolean insideWithSlack(PetalMask.Petal petal, double x, double y) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (petal.contains(x + dx * EDGE_SLACK, y + dy * EDGE_SLACK)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A square cut at a petal's border keeps exactly its part under the
     * petal's face (decision icons-slide-in-from-behind-the-tip).
     */
    @Nested
    class SquareClip {

        /** Grid points per side the straddling square's inside area is sampled at. */
        private static final int SAMPLES = 256;
        private static final double HALF_SIZE = 0.04;
        private static final double AREA_TOLERANCE = 0.01;

        private final PetalMask.Petal base = new PetalMask.Petal(START, 4 * ARC, RadialWheel.HUB_FRACTION,
                RadialWheel.TYPE_BASE_LENGTH);
        private final PetalMask.Petal card = new PetalMask.Petal(START + ARC, 1.5 * ARC, base.outer(), 0.95, base);
        private final double middle = card.start() + card.arc() / 2;

        private static double area(List<PetalMesh.Quad> quads) {
            double sum = 0;
            for (PetalMesh.Quad quad : quads) {
                List<PetalMesh.Vertex> corners = quad.corners();
                double twice = 0;
                for (int i = 0; i < corners.size(); i++) {
                    PetalMesh.Vertex from = corners.get(i);
                    PetalMesh.Vertex to = corners.get((i + 1) % corners.size());
                    twice += from.x() * to.y() - to.x() * from.y();
                }
                sum += Math.abs(twice) / 2;
            }
            return sum;
        }

        @Test
        void squareWhollyOutsideAnswersNoQuads() {
            PetalMask.Point beyond = PetalMask.Point.polar(middle, card.outer() + 2 * HALF_SIZE);

            assertTrue(PetalMesh.clipSquare(card, beyond.x(), beyond.y(), HALF_SIZE).isEmpty());
        }

        @Test
        void squareWhollyInsideAnswersItsWholeArea() {
            PetalMask.Point tip = card.tipCenter();
            double half = HALF_SIZE / 4;

            List<PetalMesh.Quad> quads = PetalMesh.clipSquare(card, tip.x(), tip.y(), half);

            assertEquals(4 * half * half, area(quads), 4 * half * half * 1e-9);
        }

        @Test
        void squareStraddlingTheTipKeepsOnlyItsPartInsideThePetal() {
            PetalMask.Point edge = PetalMask.Point.polar(middle, card.outer());
            double left = edge.x() - HALF_SIZE;
            double top = edge.y() - HALF_SIZE;
            double side = 2 * HALF_SIZE;

            List<PetalMesh.Quad> quads = PetalMesh.clipSquare(card, edge.x(), edge.y(), HALF_SIZE);

            assertFalse(quads.isEmpty());
            assertAll(quads.stream().flatMap(quad -> quad.corners().stream()).map(corner -> (Executable) () -> {
                assertTrue(insideWithSlack(card, corner.x(), corner.y()), corner + " off the petal");
                assertTrue(corner.x() >= left - EDGE_SLACK && corner.x() <= left + side + EDGE_SLACK
                        && corner.y() >= top - EDGE_SLACK && corner.y() <= top + side + EDGE_SLACK,
                        corner + " off the square");
                assertEquals((corner.x() - left) / side, corner.u(), EDGE_SLACK, corner + " u");
                assertEquals((corner.y() - top) / side, corner.v(), EDGE_SLACK, corner + " v");
            }));
            int hits = 0;
            for (int row = 0; row < SAMPLES; row++) {
                for (int column = 0; column < SAMPLES; column++) {
                    if (card.contains(left + (column + 0.5) * side / SAMPLES, top + (row + 0.5) * side / SAMPLES)) {
                        hits++;
                    }
                }
            }
            double sampled = side * side * hits / (SAMPLES * SAMPLES);
            assertTrue(sampled > 0 && sampled < side * side, "the square straddles the border");
            assertEquals(sampled, area(quads), sampled * AREA_TOLERANCE);
        }
    }
}
