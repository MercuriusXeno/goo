package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hit_or_miss split: each host kind runs and is held at load to the
 * branch it serves, so Vines loads on the struck mob and on the landing
 * (decision vines-unpack-root-and-thorn).
 */
class HitOrMissStepTest {

    private static final Step HIT = new RootStep(Expr.literal(60), Expr.literal(4), Expr.literal(1),
            Expr.literal(1.5));
    private static final Step MISS = new LingerStep(List.of());
    private static final HitOrMissStep SPLIT = new HitOrMissStep(List.of(HIT), List.of(MISS));

    @Test
    void aStruckMobRunsTheHitSteps() {
        assertEquals(List.of(HIT), SPLIT.branchFor(HostKind.ENTITY));
    }

    @Test
    void aLandingRunsTheMissSteps() {
        assertEquals(List.of(MISS), SPLIT.branchFor(HostKind.LANDING));
    }

    @Test
    void aHostThatIsNeitherRunsNothing() {
        assertTrue(SPLIT.branchFor(HostKind.TAP).isEmpty());
    }

    @Test
    void vinesLoadsForTheLandingItsMissLingersOn() {
        assertDoesNotThrow(() -> ProgramBehavior.forHost(AbilityJson.decode("leaf_vines").behaviors(),
                HostKind.LANDING));
    }
}
