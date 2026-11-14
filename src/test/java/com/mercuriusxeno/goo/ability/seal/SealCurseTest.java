package com.mercuriusxeno.goo.ability.seal;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.BranchStep;
import com.mercuriusxeno.goo.ability.program.HostVariables;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.Variables;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.OptionalDouble;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The warp curse Seal leaves (decision zone-curses-with-ender-shimmer):
 * which mobs resist it, and whether it stands.
 */
class SealCurseTest {

    private static final double ZOMBIE_MAX_HEALTH = 20;
    private static final double RAVAGER_PLUS_MAX_HEALTH = 150;

    /** The condition ender_seal.json's branch curses under. */
    private static double cursesAt(double maxHealth) {
        BranchStep resist = flatten(AbilityJson.decode("ender_seal").behaviors().stream())
                .filter(BranchStep.class::isInstance).map(BranchStep.class::cast).findFirst().orElseThrow();
        Variables mob = name -> HostVariables.MAX_HEALTH.equals(name) ? OptionalDouble.of(maxHealth)
                : OptionalDouble.empty();
        return resist.when().evaluate(mob);
    }

    private static Stream<Step> flatten(Stream<Step> steps) {
        return steps.flatMap(step -> Stream.concat(Stream.of(step), flatten(step.children())));
    }

    @Nested
    class Resist {

        @Test
        void aZombieIsCursed() {
            assertEquals(1, cursesAt(ZOMBIE_MAX_HEALTH));
        }

        @Test
        void aMobOverTheCapResists() {
            assertEquals(0, cursesAt(RAVAGER_PLUS_MAX_HEALTH));
        }
    }

    @Test
    void noCurseStandsOnAnUncursedMob() {
        assertFalse(SealCurse.NONE.stands());
        assertTrue(new SealCurse(6, 32).stands());
    }
}
