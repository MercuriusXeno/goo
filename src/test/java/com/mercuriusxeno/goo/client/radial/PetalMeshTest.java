package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the petal's edge strip as one continuous ribbon: neighboring quads
 * share their corners, so no sliver of fill shows between them where the
 * outline bends, as it does along the inner arc by the hub (decision
 * wedges-take-a-solid-edge).
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
}
