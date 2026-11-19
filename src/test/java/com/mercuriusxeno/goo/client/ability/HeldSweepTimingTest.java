package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.HeldSweepSections;
import com.mercuriusxeno.goo.ability.program.HeldSweepWorld;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The timing run: one ten second Scry hold at reach 96 and one ten second
 * ore-sense hold at radius 64 over the fixed synthetic world, each run once
 * to warm up and then {@value #RUNS} times under a profiler reading the
 * nanosecond clock, printing each part's cost in its fastest run by the
 * section it reports under, costliest first. It reads the system clock, so it stands
 * outside the unit suite, tagged {@code timing}, and runs alone as
 * {@code ./gradlew heldSweepTiming}.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
@Tag("timing")
class HeldSweepTimingTest {

    private static final int RUNS = 7;
    private static final double NANOS_A_MILLI = 1e6;
    private static final double PERCENT = 100;
    private static final Set<String> SERVER_PARTS = Set.of(HeldSweepSections.SCRY_STRIKE,
            HeldSweepSections.ORE_WALK, HeldSweepSections.PAYLOAD_SENDS);
    private static final int TICKS = HeldSweepHold.TEN_SECONDS;
    private static final int FRAMES = HeldSweepHold.TEN_SECONDS * HeldSweepHold.FRAMES_A_TICK;

    @Test
    void tenSecondHoldsRankTheirPartsByCost() {
        HeldSweepWorld world = new HeldSweepWorld();
        System.out.printf(Locale.ROOT, "held-sweep timing: %d cores, %s %s, java %s, fastest of %d runs after one warm-up%n",
                Runtime.getRuntime().availableProcessors(), System.getProperty("os.name"),
                System.getProperty("os.arch"), System.getProperty("java.version"), RUNS);
        assertTrue(rank("Scry hold, 10 s at reach 96", HeldSweepHold::scry, world) > 0);
        assertTrue(rank("Ore-sense hold, 10 s at radius 64", HeldSweepHold::oreSense, world) > 0);
    }

    /**
     * Times a hold {@value #RUNS} times after a warm-up and prints each
     * part's fastest run, since other work sharing the machine only ever
     * slows a run, and the mean beside it.
     */
    private static long rank(String name, Function<HeldSweepWorld, HeldSweepHold> start, HeldSweepWorld world) {
        SectionRecorder recorder = new SectionRecorder(System::nanoTime);
        holdOnce(start.apply(world), recorder);
        Map<String, Long> fastest = new HashMap<>();
        Map<String, Long> total = new HashMap<>();
        for (int run = 0; run < RUNS; run++) {
            recorder.reset();
            holdOnce(start.apply(world), recorder);
            recorder.spent().forEach((part, nanos) -> {
                fastest.merge(part, nanos, Math::min);
                total.merge(part, nanos, Long::sum);
            });
        }
        long parts = fastest.values().stream().mapToLong(Long::longValue).sum();
        long clientParts = fastest.entrySet().stream().filter(part -> !SERVER_PARTS.contains(part.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        System.out.printf(Locale.ROOT, "%n%s: %.1f ms a hold in the parts, %.1f ms of it on the client (fastest runs)%n",
                name, parts / NANOS_A_MILLI, clientParts / NANOS_A_MILLI);
        System.out.printf(Locale.ROOT, "  %-30s %12s %14s %8s %12s%n", "part", "ms a hold", "ms each", "share",
                "mean a hold");
        fastest.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .forEach(part -> {
                    double perHold = part.getValue();
                    boolean server = SERVER_PARTS.contains(part.getKey());
                    System.out.printf(Locale.ROOT, "  %-30s %12.2f %10.4f/%-3s %7.1f%% %12.2f%n", part.getKey(),
                            perHold / NANOS_A_MILLI, perHold / (server ? TICKS : FRAMES) / NANOS_A_MILLI,
                            server ? "tk" : "fr", PERCENT * perHold / parts,
                            total.get(part.getKey()) / (double) RUNS / NANOS_A_MILLI);
                });
        return parts;
    }

    private static void holdOnce(HeldSweepHold hold, SectionRecorder recorder) {
        try (Profiler.Scope scope = Profiler.use(recorder)) {
            hold.hold(TICKS);
        }
    }
}
