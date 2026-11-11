package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A branch step chooses its then list when its condition reads nonzero and
 * its otherwise list when it reads zero (decision rewind-fills-while-held).
 */
class BranchStepTest {

    private static final List<Step> THEN = List.of(new ExplodeStep(Expr.literal(1), ExplosionMode.NONE));
    private static final List<Step> OTHERWISE = List.of(new ExplodeStep(Expr.literal(2), ExplosionMode.NONE));

    private static BranchStep branchOn(double condition) {
        return new BranchStep(Expr.literal(condition), THEN, OTHERWISE);
    }

    @Test
    void nonzeroConditionChoosesTheThenList() {
        assertEquals(THEN, branchOn(1).chosen(Variables.NONE));
    }

    @Test
    void zeroConditionChoosesTheOtherwiseList() {
        assertEquals(OTHERWISE, branchOn(0).chosen(Variables.NONE));
    }
}
