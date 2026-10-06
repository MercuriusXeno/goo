package com.mercuriusxeno.goo.ability.world;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AbilitySplat announces a landing's burnout before its program runs, so
 * the burnout reaches clients in the arrival tick ahead of anything the
 * program does (decisions elemental-explosion-per-type,
 * splat-runs-the-program-no-fuse); a program that lingers announces none at
 * the splat, since its standing block plays the burnout as it explodes.
 */
class AbilitySplatTest {

    private static final String ANNOUNCE = "announce";
    private static final String RUN = "run";

    /**
     * Records each world action in the order the splat takes it.
     *
     * @param calls   the actions taken, in order
     * @param lingers whether the recorded program lingers
     */
    private record RecordingSplat(List<String> calls, boolean lingers) implements AbilitySplat {

        RecordingSplat(boolean lingers) {
            this(new ArrayList<>(), lingers);
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

    @Test
    void aLingeringProgramRunsWithNoBurnoutAtTheSplat() {
        RecordingSplat splat = new RecordingSplat(true);
        AbilitySplat.resolve(splat);
        assertEquals(List.of(RUN), splat.calls());
    }
}
