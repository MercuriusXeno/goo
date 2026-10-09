package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.frost.FrostCurve;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.NovaStep;
import com.mercuriusxeno.goo.ability.program.WindStep;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ghost a charging Nova shows reaches the radius its nova step resolves
 * at the hold's charge, and a program with no nova shows none.
 */
class NovaChargeGhostTest {

    private static final double EPSILON = 1e-9;
    private static final NovaStep NOVA = new NovaStep(Expr.parse("2 + 6 * charge").getOrThrow(), Expr.literal(4),
            0.05f, 0.4f, new FrostCurve(300, 0.005f, 0.5f, 3f));

    @Test
    void theGhostGrowsWithTheCharge() {
        assertEquals(2, NovaChargeGhost.reachAt(List.of(NOVA), 0f).orElseThrow(), EPSILON);
        assertEquals(5, NovaChargeGhost.reachAt(List.of(NOVA), 0.5f).orElseThrow(), EPSILON);
        assertEquals(8, NovaChargeGhost.reachAt(List.of(NOVA), 1f).orElseThrow(), EPSILON);
    }

    @Test
    void aProgramWithNoNovaShowsNoGhost() {
        assertTrue(NovaChargeGhost.reachAt(List.of(new WindStep(true)), 1f).isEmpty());
    }
}
