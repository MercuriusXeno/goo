package com.mercuriusxeno.goo.ability.rewind;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.BranchStep;
import com.mercuriusxeno.goo.ability.program.CounterStep;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TargetStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.OptionalDouble;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * aeon_rewind.json's share of the ritual per held tick: a full-health cow
 * fills in about four seconds, a wounded mob resists less and a tougher
 * mob more (decision rewind-shrinks-adult-to-baby-to-egg).
 */
class RewindShareTest {

    private static final double COW_MAX_HEALTH = 10;
    private static final double ZOMBIE_MAX_HEALTH = 20;
    private static final double RITUAL_FULL = 100;
    private static final double TICKS_PER_SECOND = 20;
    private static final double FULL_COW_SECONDS = 4;
    private static final double SECONDS_TOLERANCE = 0.25;
    private static final double EPSILON = 1e-9;

    private static TargetStep target() {
        return AbilityJson.decode("aeon_rewind").behaviors().stream().filter(TargetStep.class::isInstance)
                .map(TargetStep.class::cast).findFirst().orElseThrow();
    }

    private static Expr share() {
        return target().steps().stream().filter(CounterStep.class::isInstance).map(CounterStep.class::cast)
                .findFirst().orElseThrow().add().orElseThrow();
    }

    private static double shareAt(double health, double maxHealth) {
        Map<String, Double> values = Map.of(HostVariables.HEALTH, health, HostVariables.MAX_HEALTH, maxHealth);
        Variables mob = name -> values.containsKey(name) ? OptionalDouble.of(values.get(name)) : OptionalDouble.empty();
        return share().evaluate(mob);
    }

    @Test
    void aFullHealthCowFillsInAboutFourSeconds() {
        double seconds = RITUAL_FULL / shareAt(COW_MAX_HEALTH, COW_MAX_HEALTH) / TICKS_PER_SECOND;

        assertEquals(FULL_COW_SECONDS, seconds, SECONDS_TOLERANCE);
    }

    @Test
    void aMobAtHalfHealthFillsTwiceAsFast() {
        assertEquals(2 * shareAt(COW_MAX_HEALTH, COW_MAX_HEALTH), shareAt(COW_MAX_HEALTH / 2, COW_MAX_HEALTH), EPSILON);
    }

    @Test
    void aTougherMobAtFullHealthFillsSlower() {
        assertTrue(shareAt(ZOMBIE_MAX_HEALTH, ZOMBIE_MAX_HEALTH) < shareAt(COW_MAX_HEALTH, COW_MAX_HEALTH));
    }

    @Test
    void theRitualRegressesTheMobAtAHundred() {
        Step branch = target().steps().stream().filter(BranchStep.class::isInstance).findFirst().orElseThrow();

        assertTrue(((BranchStep) branch).when().evaluate(name -> name.equals("goo:ritual")
                ? OptionalDouble.of(RITUAL_FULL) : OptionalDouble.empty()) != 0);
    }
}
