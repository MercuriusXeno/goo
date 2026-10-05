package com.mercuriusxeno.goo.block.ability;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ChainMarkerSplat announces the burnout before any removal, for a program
 * that finishes the tick its blob splats and for one that keeps running.
 */
class ChainMarkerSplatTest {

    private static final String ANNOUNCE = "announce";
    private static final String LOAD = "load";
    private static final String FIRST_TICK = "firstTick";
    private static final String REMOVE = "remove";
    private static final String SYNC = "sync";

    /**
     * Records each world action in the order the splat takes it.
     *
     * @param hasProgram   whether the marker holds a program
     * @param keepsRunning whether the program runs past its first tick
     * @param calls        the actions taken, in order
     */
    private record RecordingSplat(boolean hasProgram, boolean keepsRunning, List<String> calls)
            implements ChainMarkerSplat {

        RecordingSplat(boolean hasProgram, boolean keepsRunning) {
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
        RecordingSplat splat = new RecordingSplat(true, false);
        ChainMarkerSplat.resolve(splat);
        assertEquals(List.of(ANNOUNCE, LOAD, FIRST_TICK, REMOVE), splat.calls());
    }

    @Test
    void runningProgramAnnouncesAndStays() {
        RecordingSplat splat = new RecordingSplat(true, true);
        ChainMarkerSplat.resolve(splat);
        assertEquals(List.of(ANNOUNCE, LOAD, FIRST_TICK, SYNC), splat.calls());
    }

    @Test
    void markerWithoutAProgramAnnouncesBeforeItIsRemoved() {
        RecordingSplat splat = new RecordingSplat(false, false);
        ChainMarkerSplat.resolve(splat);
        assertEquals(List.of(ANNOUNCE, LOAD, REMOVE), splat.calls());
    }
}
