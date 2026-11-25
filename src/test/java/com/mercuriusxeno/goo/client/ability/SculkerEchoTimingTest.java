package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.SectionRecorder;
import net.minecraft.util.profiling.Profiler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The timing run: one ten second Sculker hold over the fixed synthetic world
 * and stream of sounds ({@link SculkerEchoHold}), run once to warm up and
 * then {@value #RUNS} times under a profiler reading the nanosecond clock,
 * printing each part's cost in its fastest run by the section it reports
 * under, costliest first, beside how often it ran and its cost each run, so
 * a part run once a second shows the hitch it makes. A section run inside another is marked, its time
 * also standing in its parent's. It reads the system clock, so it stands
 * outside the unit suite, tagged {@code timing}, and runs alone as
 * {@code ./gradlew sculkerEchoTiming}.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
@Tag("timing")
class SculkerEchoTimingTest {

    private static final int RUNS = 7;
    private static final double NANOS_A_MILLI = 1e6;
    private static final double PERCENT = 100;
    private static final int TICKS = SculkerEchoHold.TEN_SECONDS;
    private static final int FRAMES = SculkerEchoHold.TEN_SECONDS * SculkerEchoHold.FRAMES_A_TICK;
    /** The tick's parts; every other section is a frame's. */
    private static final Set<String> TICK_PARTS = Set.of(SculkerEchoSections.NOISES, SculkerEchoSections.HEAR,
            SculkerEchoSections.RINGS, SculkerEchoSections.FADE, SculkerEchoSections.FAINTEST,
            SculkerEchoSections.QUADS_TRIM);
    /** The sections run inside another: the shroom rescan inside the nearby read. */
    private static final Set<String> NESTED = Set.of(SculkerEchoSections.SHROOM_RESCAN);

    @Test
    void aTenSecondHoldRanksTheEchosPartsByCost() {
        System.out.printf(Locale.ROOT, "sculker-echo timing: %d cores, %s %s, java %s, fastest of %d runs after one warm-up%n",
                Runtime.getRuntime().availableProcessors(), System.getProperty("os.name"),
                System.getProperty("os.arch"), System.getProperty("java.version"), RUNS);
        SectionRecorder recorder = new SectionRecorder(System::nanoTime);
        holdOnce(recorder);
        Map<String, Long> fastest = new HashMap<>();
        Map<String, Long> total = new HashMap<>();
        long remembered = 0;
        long drawn = 0;
        long quads = 0;
        for (int run = 0; run < RUNS; run++) {
            recorder.reset();
            SculkerEchoHold hold = holdOnce(recorder);
            remembered = hold.remembered();
            drawn = hold.drawn();
            quads = hold.quads();
            recorder.spent().forEach((part, nanos) -> {
                fastest.merge(part, nanos, Math::min);
                total.merge(part, nanos, Long::sum);
            });
        }
        long parts = fastest.entrySet().stream().filter(part -> !NESTED.contains(part.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        System.out.printf(Locale.ROOT, "%nSculker hold, 10 s: %.1f ms a hold in the parts (fastest runs); the hold ends"
                        + " remembering %d faces, drawing %d blocks a frame, %d quads submitted over the hold%n",
                parts / NANOS_A_MILLI, remembered, drawn, quads);
        Map<String, Long> pushes = recorder.pushed().stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        System.out.printf(Locale.ROOT, "  %-34s %12s %14s %8s %12s %8s %12s%n", "part", "ms a hold", "ms each", "share",
                "mean a hold", "pushes", "ms a push");
        fastest.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .forEach(part -> printPart(part.getKey(), part.getValue(), parts, total.get(part.getKey()),
                        pushes.get(part.getKey())));
        assertTrue(parts > 0, "the hold spent time in its parts");
    }

    private static void printPart(String part, long nanos, long parts, long total, long pushes) {
        boolean tick = TICK_PARTS.contains(part);
        String name = NESTED.contains(part) ? "  (within) " + part : part;
        System.out.printf(Locale.ROOT, "  %-34s %12.2f %10.4f/%-3s %7.1f%% %12.2f %8d %12.4f%n", name,
                nanos / NANOS_A_MILLI, (double) nanos / (tick ? TICKS : FRAMES) / NANOS_A_MILLI, tick ? "tk" : "fr",
                PERCENT * nanos / parts, total / (double) RUNS / NANOS_A_MILLI, pushes,
                (double) nanos / pushes / NANOS_A_MILLI);
    }

    private static SculkerEchoHold holdOnce(SectionRecorder recorder) {
        SculkerEchoHold hold = new SculkerEchoHold();
        try (Profiler.Scope scope = Profiler.use(recorder)) {
            hold.hold(TICKS);
        }
        return hold;
    }
}
