package com.mercuriusxeno.goo.ability.program;

import com.mojang.datafixers.util.Unit;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hit_or_miss split: each host kind runs and is held at load to the
 * branch it serves, so one program loads on the struck mob and on the
 * landing.
 */
class HitOrMissStepTest {

    private static final Step HIT = LeafSteps.IGNITE.step(Expr.literal(5));
    private static final Step MISS = LeafSteps.LIGHT_FIRE.step(Unit.INSTANCE);
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
        assertTrue(SPLIT.branchFor(HostKind.SURFACE).isEmpty());
    }
}
