package com.mercuriusxeno.goo.ability.world;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AbilitySplat announces a landing's burnout before its program runs, so
 * the burnout reaches clients in the arrival tick ahead of anything the
 * program does (decisions elemental-explosion-per-type,
 * splat-runs-the-program-no-fuse).
 */
class AbilitySplatTest {

    private static final String ANNOUNCE = "announce";
    private static final String RUN = "run";

    /**
     * Records each world action in the order the splat takes it.
     *
     * @param calls the actions taken, in order
     */
    private record RecordingSplat(List<String> calls) implements AbilitySplat {

        RecordingSplat() {
            this(new ArrayList<>());
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
        RecordingSplat splat = new RecordingSplat();
        AbilitySplat.resolve(splat);
        assertEquals(List.of(ANNOUNCE, RUN), splat.calls());
    }
}
