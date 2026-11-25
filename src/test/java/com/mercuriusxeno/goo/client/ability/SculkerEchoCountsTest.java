package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SectionRecorder;
import com.mercuriusxeno.goo.client.TranslucentVisualPath;
import net.minecraft.util.profiling.Profiler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One tick's, one frame's and one shroom rescan's work over the fixed
 * synthetic world and stream of sounds, ten seconds into a Sculker hold,
 * counted by the counters each part reports under: the parts the echo tests
 * standing before task sculker-echo-cost-is-ranked-past-the-landed-fix left
 * uncounted. Each count is what the code at that task's commit does, the
 * baseline a cut to the echo's cost is read against.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
class SculkerEchoCountsTest {

    /** The ticks before the one recorded: the two hundredth hears every sound in the stream. */
    private static final int SETTLE = SculkerEchoHold.TEN_SECONDS - 1;

    private static Map<String, Long> recorded(Runnable work) {
        SectionRecorder recorder = SectionRecorder.untimed();
        try (Profiler.Scope scope = Profiler.use(recorder)) {
            work.run();
        }
        return recorder.counted();
    }

    private static long count(Map<String, Long> counted, String counter) {
        return counted.getOrDefault(counter, -1L);
    }

    /** One tick's bookkeeping, every sound of the stream heard on it. */
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class OneTick {

        private final Map<String, Long> counted = tickCounts();

        private Map<String, Long> tickCounts() {
            SculkerEchoHold hold = new SculkerEchoHold();
            hold.hold(SETTLE);
            return recorded(hold::tick);
        }

        @Test
        void theRingsWalkTheirShells() {
            assertEquals(5_856L, count(counted, SculkerEchoSections.SHELL_CELLS_COUNTER), "shell cells walked");
        }

        @Test
        void aSteadyHoldKeepsEveryCellsAnswer() {
            assertEquals(0L, count(counted, SculkerEchoSections.CELL_READS_COUNTER), "cells read from the world");
        }

        @Test
        void theRingsLightTheFacesTheyCross() {
            assertEquals(1_068L, count(counted, SculkerEchoSections.FACES_LIT_COUNTER), "faces lit");
        }

        @Test
        void theFadeSweepWeighsEveryRememberedFace() {
            assertEquals(6_309L, count(counted, SculkerEchoSections.FADE_WEIGHED_COUNTER), "faces the fade weighed");
        }

        @Test
        void aMemoryShortOfItsMostSortsNothing() {
            assertEquals(0L, count(counted, SculkerEchoSections.FAINTEST_SORTED_COUNTER), "faces sorted for the faintest");
        }

        @Test
        void eachSoundPassesOverTheNoisesWaiting() {
            assertEquals(1L, count(counted, SculkerEchoSections.NOISES_WEIGHED_COUNTER), "waiting noises weighed");
        }
    }

    /** One frame's draw, no shroom rescan due on it. */
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class OneFrame {

        private final Map<String, Long> counted = frameCounts();

        private Map<String, Long> frameCounts() {
            SculkerEchoHold hold = new SculkerEchoHold();
            hold.hold(SculkerEchoHold.TEN_SECONDS);
            return recorded(hold::frame);
        }

        @Test
        void theFrameWeighsTheFacesOfEverySectionInView() {
            assertEquals(3_708L, count(counted, SculkerEchoSections.IN_VIEW_WEIGHED_COUNTER), "faces weighed in view");
        }

        @Test
        void theFrameReadsEveryShroomBlockKept() {
            assertEquals(512L, count(counted, SculkerEchoSections.SHROOM_STATES_COUNTER), "shroom states read");
        }

        @Test
        void theFrameSubmitsEveryDrawnBlocksQuads() {
            assertEquals(3_688L, count(counted, SculkerEchoSections.QUADS_SUBMITTED_COUNTER), "quads submitted");
        }

        @Test
        void aSteadyHoldKeepsEveryBlocksQuads() {
            assertEquals(0L, count(counted, SculkerEchoSections.MODELS_COLLECTED_COUNTER), "models collected");
        }

        @Test
        void thePathSortsTheEchosDraw() {
            assertEquals(1L, count(counted, TranslucentVisualPath.PATH_SORTED_COUNTER), "draws the path sorted");
        }
    }

    /** The frame the once-a-second shroom scan runs on. */
    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class OneRescan {

        /** Every section within the vision's reach that may hold sculk, as the first scan of a hold reads them. */
        private static final long SECTIONS_IN_REACH = 729;
        private static final long BLOCKS_A_SECTION = 4_096;

        private final Map<String, Long> first = firstScanCounts();
        private final Map<String, Long> counted = rescanCounts();

        private Map<String, Long> firstScanCounts() {
            SculkerEchoHold hold = new SculkerEchoHold();
            hold.tick();
            return recorded(hold::frame);
        }

        private Map<String, Long> rescanCounts() {
            SculkerEchoHold hold = new SculkerEchoHold();
            hold.hold(SculkerEchoHold.TEN_SECONDS);
            hold.tick();
            return recorded(hold::frame);
        }

        @Test
        void theFirstScanReadsEverySectionThatMayHoldSculk() {
            assertEquals(SECTIONS_IN_REACH, count(first, SculkerEchoSections.SHROOM_SECTIONS_COUNTER),
                    "sections the first scan read");
            assertEquals(SECTIONS_IN_REACH * BLOCKS_A_SECTION, count(first, SculkerEchoSections.SHROOM_BLOCKS_COUNTER),
                    "blocks the first scan read");
        }

        /**
         * A rescan over sections no block change or chunk load reached reads
         * none of them again; at the parent commit of task
         * sculker-echo-costs-less-each-frame-and-tick each rescan read all 729,
         * 2,985,984 blocks.
         */
        @Test
        void aRescanReadsNoSectionAnEarlierScanAlreadyRead() {
            assertEquals(0L, count(counted, SculkerEchoSections.SHROOM_SECTIONS_COUNTER), "sections the rescan read");
            assertEquals(0L, count(counted, SculkerEchoSections.SHROOM_BLOCKS_COUNTER), "blocks the rescan read");
        }

        @Test
        void aBlockChangeHasTheNextRescanReadItsSectionAlone() {
            SculkerEchoHold hold = new SculkerEchoHold();
            hold.hold(SculkerEchoHold.TEN_SECONDS);
            hold.blockChanged(SculkerEchoHold.PLAYER_FLOOR);
            hold.tick();
            Map<String, Long> changed = recorded(hold::frame);
            assertEquals(1L, count(changed, SculkerEchoSections.SHROOM_SECTIONS_COUNTER), "sections the rescan read");
            assertEquals(BLOCKS_A_SECTION, count(changed, SculkerEchoSections.SHROOM_BLOCKS_COUNTER),
                    "blocks the rescan read");
        }

        @Test
        void aChunkLoadHasTheNextRescanReadItsColumnAlone() {
            SculkerEchoHold hold = new SculkerEchoHold();
            hold.hold(SculkerEchoHold.TEN_SECONDS);
            hold.chunkLoaded(0, 0);
            hold.tick();
            Map<String, Long> loaded = recorded(hold::frame);
            assertEquals(SculkerEchoHold.SECTIONS_A_COLUMN_IN_REACH,
                    count(loaded, SculkerEchoSections.SHROOM_SECTIONS_COUNTER), "sections the rescan read");
        }
    }
}
