package com.mercuriusxeno.goo.block.ability;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ChainMarkerDetonation announces the burnout before any removal, for a
 * program that finishes the tick it fires and for one that keeps running.
 */
class ChainMarkerDetonationTest {

    private static final String ANNOUNCE = "announce";
    private static final String LOAD = "load";
    private static final String FIRST_TICK = "firstTick";
    private static final String REMOVE = "remove";
    private static final String SYNC = "sync";

    /**
     * Records each world action in the order the detonation takes it.
     *
     * @param hasProgram   whether the marker holds a program
     * @param keepsRunning whether the program runs past its first tick
     * @param calls        the actions taken, in order
     */
    private record RecordingDetonation(boolean hasProgram, boolean keepsRunning, List<String> calls)
            implements ChainMarkerDetonation {

        RecordingDetonation(boolean hasProgram, boolean keepsRunning) {
            this(hasProgram, keepsRunning, new ArrayList<>());
        }

        @Override
        public void announceBurnout() {
            calls.add(ANNOUNCE);
        }

        @Override
        public boolean loadProgram() {
            calls.add(LOAD);
            return hasProgram;
        }

        @Override
        public boolean runFirstTick() {
            calls.add(FIRST_TICK);
            return keepsRunning;
        }

        @Override
        public void removeMarker() {
            calls.add(REMOVE);
        }

        @Override
        public void syncRunningProgram() {
            calls.add(SYNC);
        }
    }

    @Test
    void oneTickProgramAnnouncesBeforeTheMarkerIsRemoved() {
        RecordingDetonation detonation = new RecordingDetonation(true, false);
        ChainMarkerDetonation.fire(detonation);
        assertEquals(List.of(ANNOUNCE, LOAD, FIRST_TICK, REMOVE), detonation.calls());
    }

    @Test
    void runningProgramAnnouncesAndStays() {
        RecordingDetonation detonation = new RecordingDetonation(true, true);
        ChainMarkerDetonation.fire(detonation);
        assertEquals(List.of(ANNOUNCE, LOAD, FIRST_TICK, SYNC), detonation.calls());
    }

    @Test
    void markerWithoutAProgramAnnouncesBeforeItIsRemoved() {
        RecordingDetonation detonation = new RecordingDetonation(false, false);
        ChainMarkerDetonation.fire(detonation);
        assertEquals(List.of(ANNOUNCE, LOAD, REMOVE), detonation.calls());
    }
}
