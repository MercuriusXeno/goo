package com.mercuriusxeno.goo.ability.program;

import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.metrics.MetricCategory;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A profiler that records every section pushed and the time spent in each,
 * and every counter's total, the time read off a clock it is handed: a unit test hands it a stopped clock and
 * reads the sections alone, the timing run hands it the nanosecond clock.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 */
public final class SectionRecorder implements ProfilerFiller {

    private final LongSupplier clock;
    private final List<String> pushed = new ArrayList<>();
    private final Deque<String> open = new ArrayDeque<>();
    private final Deque<Long> openedAt = new ArrayDeque<>();
    private final Map<String, Long> spent = new LinkedHashMap<>();
    private final Map<String, Long> counted = new LinkedHashMap<>();

    /**
     * @param clock the clock each section's time is read from, in nanoseconds
     */
    public SectionRecorder(LongSupplier clock) {
        this.clock = clock;
    }

    /** @return a recorder whose clock never moves, for a test reading the sections alone */
    public static SectionRecorder untimed() {
        return new SectionRecorder(() -> 0L);
    }

    /** @return every section pushed, in order */
    public List<String> pushed() {
        return List.copyOf(pushed);
    }

    /** @return true once every section pushed has been popped */
    public boolean balanced() {
        return open.isEmpty();
    }

    /** @return the nanoseconds spent in each section, by name, in the order first pushed */
    public Map<String, Long> spent() {
        return Map.copyOf(spent);
    }

    /** @return each counter's total, by name */
    public Map<String, Long> counted() {
        return Map.copyOf(counted);
    }

    /** Forgets every section and counter recorded, so a timed run starts from nothing. */
    public void reset() {
        pushed.clear();
        spent.clear();
        counted.clear();
    }

    @Override
    public void push(String name) {
        pushed.add(name);
        open.push(name);
        openedAt.push(clock.getAsLong());
    }

    @Override
    public void push(Supplier<String> name) {
        push(name.get());
    }

    @Override
    public void pop() {
        if (open.isEmpty()) {
            throw new IllegalStateException("popped a section never pushed");
        }
        spent.merge(open.pop(), clock.getAsLong() - openedAt.pop(), Long::sum);
    }

    @Override
    public void popPush(String name) {
        pop();
        push(name);
    }

    @Override
    public void popPush(Supplier<String> name) {
        popPush(name.get());
    }

    @Override
    public void startTick() {
        // a tick's bounds carry no section
    }

    @Override
    public void endTick() {
        // a tick's bounds carry no section
    }

    @Override
    public void markForCharting(MetricCategory category) {
        // charting reads no section
    }

    @Override
    public void incrementCounter(String name, int count) {
        counted.merge(name, (long) count, Long::sum);
    }

    @Override
    public void incrementCounter(Supplier<String> name, int count) {
        incrementCounter(name.get(), count);
    }
}
