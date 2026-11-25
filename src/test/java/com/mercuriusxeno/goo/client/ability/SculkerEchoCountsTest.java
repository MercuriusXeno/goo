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

        private final Map<String, Long> counted = rescanCounts();

        private Map<String, Long> rescanCounts() {
            SculkerEchoHold hold = new SculkerEchoHold();
            hold.hold(SculkerEchoHold.TEN_SECONDS);
            hold.tick();
            return recorded(hold::frame);
        }

        @Test
        void theScanReadsEverySectionThatMayHoldSculk() {
            assertEquals(729L, count(counted, SculkerEchoSections.SHROOM_SECTIONS_COUNTER), "sections scanned");
        }

        @Test
        void theScanReadsEveryBlockOfThoseSections() {
            assertEquals(2_985_984L, count(counted, SculkerEchoSections.SHROOM_BLOCKS_COUNTER), "blocks scanned");
        }
    }
}
