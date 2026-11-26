package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SectionRecorder;
import com.mercuriusxeno.goo.client.TranslucentVisualPath;
import net.minecraft.util.profiling.Profiler;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every part of Sculker's echo work a headless hold reaches, per tick, per
 * sound and per frame, the path's sort key and sort among them, reports
 * under a profiler section or counter of its own, so a profile of a lagging
 * echo names the part the time went to.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
class SculkerEchoSectionsTest {

    /** Ticks before the two recorded: the fortieth hears every sound in the stream and the forty-first rescans. */
    private static final int SETTLE = 39;
    private static final int RECORDED_TICKS = 2;

    @Test
    void everyHeadlessPartOfTheEchoReportsUnderASectionOfItsOwn() {
        SectionRecorder recorder = recordedHold();
        List<String> sections = List.of(SculkerEchoSections.NOISES, SculkerEchoSections.HEAR,
                SculkerEchoSections.RINGS, SculkerEchoSections.FADE, SculkerEchoSections.FAINTEST,
                SculkerEchoSections.QUADS_TRIM, TranslucentVisualPath.PATH_COLLECT_SECTION,
                TranslucentVisualPath.PATH_SORT_SECTION, SculkerEchoSections.IN_VIEW,
                SculkerEchoSections.SHROOM_NEARBY, SculkerEchoSections.SHROOM_RESCAN,
                SculkerEchoSections.SHROOM_MERGE, SculkerEchoSections.SUBMIT);
        Set<String> pushed = Set.copyOf(recorder.pushed());
        assertEquals(List.of(), sections.stream().filter(section -> !pushed.contains(section)).toList(),
                "sections no part pushed over two ticks and their frames");
        assertTrue(recorder.balanced(), "every section pushed was popped");
    }

    @Test
    void everyHeadlessCountOfTheEchoReportsUnderACounterOfItsOwn() {
        SectionRecorder recorder = recordedHold();
        List<String> counters = List.of(SculkerEchoSections.SHELL_CELLS_COUNTER,
                SculkerEchoSections.CELL_READS_COUNTER, SculkerEchoSections.FACES_LIT_COUNTER,
                SculkerEchoSections.FADE_WEIGHED_COUNTER, SculkerEchoSections.FAINTEST_SORTED_COUNTER,
                SculkerEchoSections.NOISES_WEIGHED_COUNTER, SculkerEchoSections.IN_VIEW_WEIGHED_COUNTER,
                SculkerEchoSections.MODELS_COLLECTED_COUNTER, SculkerEchoSections.QUADS_SUBMITTED_COUNTER,
                SculkerEchoSections.SHROOM_SECTIONS_COUNTER, SculkerEchoSections.SHROOM_BLOCKS_COUNTER,
                SculkerEchoSections.SHROOM_STATES_COUNTER, TranslucentVisualPath.PATH_SORTED_COUNTER);
        Set<String> counted = recorder.counted().keySet();
        assertEquals(List.of(), counters.stream().filter(counter -> !counted.contains(counter)).toList(),
                "counters no part counted over two ticks and their frames");
    }

    private static SectionRecorder recordedHold() {
        SculkerEchoHold hold = new SculkerEchoHold();
        hold.hold(SETTLE);
        SectionRecorder recorder = SectionRecorder.untimed();
        try (Profiler.Scope scope = Profiler.use(recorder)) {
            hold.hold(RECORDED_TICKS);
        }
        return recorder;
    }
}
