package com.mercuriusxeno.goo.ability.rewind;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.BranchStep;
import com.mercuriusxeno.goo.ability.program.CounterStep;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.RegressStep;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.TargetStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * yore_rewind.json's ritual per held tick: every mob's counter adds a flat
 * 1.25, whatever its health, so a hundred fills in eighty held ticks; a clock
 * ticks on every fourth held tick; the ritual regresses the mob at a hundred
 * (decisions rewind-fills-while-held, rewind-shrinks-adult-to-baby-to-egg).
 */
class RewindShareTest {

    private static final double RITUAL_FULL = 100;
    private static final double SHARE = 1.25;
    private static final int TICKS_PER_CLOCK_TICK = 4;
    private static final String RITUAL = "goo:ritual";

    private static TargetStep target() {
        return AbilityJson.decode("yore_rewind").behaviors().stream().filter(TargetStep.class::isInstance)
                .map(TargetStep.class::cast).findFirst().orElseThrow();
    }

    private static Expr share() {
        return target().steps().stream().filter(CounterStep.class::isInstance).map(CounterStep.class::cast)
                .findFirst().orElseThrow().add().orElseThrow();
    }

    private static BranchStep branchRunning(Class<?> stepClass) {
        return target().steps().stream().filter(BranchStep.class::isInstance).map(BranchStep.class::cast)
                .filter(branch -> branch.then().stream().anyMatch(stepClass::isInstance)).findFirst().orElseThrow();
    }

    private static boolean firesAt(BranchStep branch, double ritual) {
        return branch.when().evaluate(name -> name.equals(RITUAL) ? OptionalDouble.of(ritual)
                : OptionalDouble.empty()) != 0;
    }

    // decision rewind-fills-while-held
    @Test
    void theRitualCounterAddsAFlatOneAndAQuarterEachHeldTick() {
        assertEquals(Expr.literal(SHARE), share());
    }

    @Test
    void theClockTicksOnEveryFourthHeldTick() {
        double share = share().evaluate(Variables.NONE);
        BranchStep clock = branchRunning(SoundStep.class);

        long ticks = IntStream.rangeClosed(1, (int) (RITUAL_FULL / share)).filter(held -> firesAt(clock, held * share))
                .count();

        assertEquals((long) (RITUAL_FULL / share) / TICKS_PER_CLOCK_TICK, ticks);
    }

    @Test
    void theRitualRegressesTheMobAtAHundred() {
        BranchStep regress = branchRunning(RegressStep.class);

        assertTrue(firesAt(regress, RITUAL_FULL));
        assertTrue(!firesAt(regress, RITUAL_FULL - share().evaluate(Variables.NONE)));
    }
}
