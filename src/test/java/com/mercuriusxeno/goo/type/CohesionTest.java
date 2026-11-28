package com.mercuriusxeno.goo.type;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the cohesion ladder: four rungs, runny 0, loose 1, soft 2 and thick 4, thick the top.
 * cohesion-is-the-step-the-goo-holds
 */
class CohesionTest {

    @Test
    void theLadderHoldsFourRungsUpToThick() {
        assertEquals(List.of(Cohesion.RUNNY, Cohesion.LOOSE, Cohesion.SOFT, Cohesion.THICK),
                List.of(Cohesion.values()));
        assertEquals(List.of(0, 1, 2, 4), Arrays.stream(Cohesion.values()).map(Cohesion::threshold).toList());
    }
}
