package com.mercuriusxeno.goo.ability.rewind;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.BranchStep;
import com.mercuriusxeno.goo.ability.program.CounterStep;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.RegressStep;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.TargetStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * aeon_rewind.json's ritual per held tick: every mob fills at one flat rate,
 * about four seconds of holding, whatever its health; a clock ticks on
 * every fourth held tick; the ritual regresses the mob at a hundred
 * (decisions rewind-fills-while-held, rewind-shrinks-adult-to-baby-to-egg).
 */
class RewindShareTest {

    private static final double COW_MAX_HEALTH = 10;
    private static final double ZOMBIE_MAX_HEALTH = 20;
    private static final double RITUAL_FULL = 100;
    private static final double TICKS_PER_SECOND = 20;
    private static final double FILL_SECONDS = 4;
    private static final double SECONDS_TOLERANCE = 0.25;
    private static final double EPSILON = 1e-9;
    private static final int TICKS_PER_CLOCK_TICK = 4;
    private static final String RITUAL = "goo:ritual";

    private static TargetStep target() {
        return AbilityJson.decode("aeon_rewind").behaviors().stream().filter(TargetStep.class::isInstance)
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

    private static double shareAt(double health, double maxHealth) {
        Map<String, Double> values = Map.of(HostVariables.HEALTH, health, HostVariables.MAX_HEALTH, maxHealth);
        Variables mob = name -> values.containsKey(name) ? OptionalDouble.of(values.get(name)) : OptionalDouble.empty();
        return share().evaluate(mob);
    }

    private static boolean firesAt(BranchStep branch, double ritual) {
        return branch.when().evaluate(name -> name.equals(RITUAL) ? OptionalDouble.of(ritual)
                : OptionalDouble.empty()) != 0;
    }

    @Test
    void aFullHealthCowFillsInAboutFourSeconds() {
        double seconds = RITUAL_FULL / shareAt(COW_MAX_HEALTH, COW_MAX_HEALTH) / TICKS_PER_SECOND;

        assertEquals(FILL_SECONDS, seconds, SECONDS_TOLERANCE);
    }

    @Test
    void aWoundedMobFillsNoFaster() {
        assertEquals(shareAt(COW_MAX_HEALTH, COW_MAX_HEALTH), shareAt(1, COW_MAX_HEALTH), EPSILON);
    }

    @Test
    void aTougherMobFillsNoSlower() {
        assertEquals(shareAt(COW_MAX_HEALTH, COW_MAX_HEALTH), shareAt(ZOMBIE_MAX_HEALTH, ZOMBIE_MAX_HEALTH), EPSILON);
    }

    @Test
    void theClockTicksOnEveryFourthHeldTick() {
        double share = shareAt(COW_MAX_HEALTH, COW_MAX_HEALTH);
        BranchStep clock = branchRunning(SoundStep.class);

        long ticks = IntStream.rangeClosed(1, (int) (RITUAL_FULL / share)).filter(held -> firesAt(clock, held * share))
                .count();

        assertEquals((long) (RITUAL_FULL / share) / TICKS_PER_CLOCK_TICK, ticks);
    }

    @Test
    void theRitualRegressesTheMobAtAHundred() {
        BranchStep regress = branchRunning(RegressStep.class);

        assertTrue(firesAt(regress, RITUAL_FULL));
        assertTrue(!firesAt(regress, RITUAL_FULL - shareAt(COW_MAX_HEALTH, COW_MAX_HEALTH)));
    }
}
