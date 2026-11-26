package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.AbilityJson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * AbilitySplat announces a landing's burnout before its program runs, so
 * the burnout reaches clients in the arrival tick ahead of anything the
 * program does (decisions elemental-explosion-per-type,
 * splat-runs-the-program-no-fuse); a blob turning into its own block
 * announces none.
 */
class AbilitySplatTest {

    private static final String ANNOUNCE = "announce";
    private static final String RUN = "run";

    /**
     * Records each world action in the order the splat takes it.
     *
     * @param calls             the actions taken, in order
     * @param turnsIntoItsBlock whether the recorded blob turns into the block its program places
     */
    private record RecordingSplat(List<String> calls, boolean turnsIntoItsBlock) implements AbilitySplat {

        RecordingSplat(boolean turnsIntoItsBlock) {
            this(new ArrayList<>(), turnsIntoItsBlock);
        }

        @Override
        public void announceBurnout() {
            calls.add(ANNOUNCE);
        }

        @Override
        public void runProgram() {
            calls.add(RUN);
        }
    }

    @Test
    void theBurnoutIsAnnouncedBeforeTheProgramRuns() {
        RecordingSplat splat = new RecordingSplat(false);
        AbilitySplat.resolve(splat);
        assertEquals(List.of(ANNOUNCE, RUN), splat.calls());
    }

    /**
     * Prism's blob morphs into the column it places, so no burst plays at its splat.
     * decision prism-is-one-pointed-quartz-column
     */
    @Test
    void aBlobTurningIntoThePrismRunsWithNoBurnoutAtTheSplat() {
        RecordingSplat splat = new RecordingSplat(AbilityImpact.turnsIntoAPrism(AbilityJson.decode("crystal_prism")));
        AbilitySplat.resolve(splat);
        assertEquals(List.of(RUN), splat.calls());
    }

    @Test
    void anotherCrystalLandingStillTurnsIntoNoPrism() {
        assertFalse(AbilityImpact.turnsIntoAPrism(AbilityJson.decode("crystal_knives")));
    }
}
