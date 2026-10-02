package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        List<PetalMesh.Quad> edge = PetalMesh.edge(petal, PetalMesh.EDGE_THICKNESS);
        for (int i = 0; i < edge.size(); i++) {
            Set<String> shared = corners(edge.get(i));
            shared.retainAll(corners(edge.get((i + 1) % edge.size())));
            assertTrue(shared.size() >= 2, "quad " + i + " shares a side with the next");
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
