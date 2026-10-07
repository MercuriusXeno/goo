package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.AbilityJson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AbilitySplat announces a landing's burnout before its program runs, so
 * the burnout reaches clients in the arrival tick ahead of anything the
 * program does (decisions elemental-explosion-per-type,
 * splat-runs-the-program-no-fuse); a program whose standing block explodes
 * later announces none at the splat, since that block plays the burnout as it
 * explodes.
 */
class AbilitySplatTest {

    private static final String ANNOUNCE = "announce";
    private static final String RUN = "run";

    /**
     * Records each world action in the order the splat takes it.
     *
     * @param calls   the actions taken, in order
     * @param explodesLater whether the recorded program's standing block explodes later
     */
    private record RecordingSplat(List<String> calls, boolean explodesLater) implements AbilitySplat {

        RecordingSplat(boolean explodesLater) {
            this(new ArrayList<>(), explodesLater);
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
     * Razor's cloud lingers through a field effect and never explodes, so its
     * burnout, the prism dome, plays at the splat.
     * decision diagnose-then-restore-the-razor-dome
     */
    @Test
    void aLingeringCloudThatNeverExplodesAnnouncesItsBurnoutBeforeItsProgramRuns() {
        RecordingSplat splat = new RecordingSplat(AbilityImpact.explodesLater(AbilityJson.decode("crystal_cloud")));
        AbilitySplat.resolve(splat);
        assertEquals(List.of(ANNOUNCE, RUN), splat.calls());
    }

    @Test
    void theMineRunsWithNoBurnoutAtTheSplat() {
        RecordingSplat splat = new RecordingSplat(AbilityImpact.explodesLater(AbilityJson.decode("unstable_proximity_mine")));
        AbilitySplat.resolve(splat);
        assertEquals(List.of(RUN), splat.calls());
    }

    @Test
    void aProgramThatExplodesLaterRunsWithNoBurnoutAtTheSplat() {
        RecordingSplat splat = new RecordingSplat(true);
        AbilitySplat.resolve(splat);
        assertEquals(List.of(RUN), splat.calls());
    }
}
