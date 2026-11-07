package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vignette's tendrils are a live population, not a fixed set: they
 * sprout at random places along the edges at random times, each with its own
 * length, thickness and wandering path that may fork, and each withers and
 * goes in its time, so the count on screen drifts and no layout repeats
 * (decisions sight-lengthens-shift-and-outlines-fungus and
 * sculker-sight-persists-where-echoes-have-touched).
 */
class TendrilVignetteTest {

    private static final long SEED = 7L;
    private static final int SETTLED = 400;
    private static final int SAMPLES_UNTIL = 2000;
    private static final int SAMPLE_EVERY = 50;
    private static final int DISTINCT_COUNTS = 3;

    private static TendrilVignette advancedTo(int ticks) {
        TendrilVignette vignette = new TendrilVignette(SEED);
        for (int tick = 0; tick <= ticks; tick++) {
            vignette.advance(tick);
        }
        return vignette;
    }

    @Test
    void aPopulationSproutsAndTheCountDrifts() {
        TendrilVignette vignette = new TendrilVignette(SEED);
        Set<Integer> counts = new HashSet<>();
        for (int tick = 0; tick <= SAMPLES_UNTIL; tick++) {
            vignette.advance(tick);
            if (tick >= SETTLED && tick % SAMPLE_EVERY == 0) {
                counts.add(vignette.live().size());
            }
        }
        assertFalse(counts.contains(0), "tendrils should stand once the population has settled: " + counts);
        assertTrue(counts.size() >= DISTINCT_COUNTS, "the count on screen should drift: " + counts);
    }

    @Test
    void noTwoTendrilsRootAtOnePlaceOrReachOneLength() {
        List<TendrilVignette.Tendril> live = advancedTo(SETTLED).live();
        Set<Float> roots = new HashSet<>();
        Set<Float> lengths = new HashSet<>();
        live.forEach(tendril -> {
            roots.add(tendril.along());
            lengths.add(tendril.length());
        });
        assertTrue(live.size() > 1 && roots.size() == live.size() && lengths.size() == live.size(),
                "every tendril should root and reach on its own");
    }

    @Test
    void someForkSomeRootThickAndTheRestDoNot() {
        List<TendrilVignette.Tendril> live = advancedTo(SAMPLES_UNTIL).live();
        assertTrue(live.stream().anyMatch(TendrilVignette.Tendril::forks)
                && live.stream().anyMatch(tendril -> !tendril.forks()), "some tendrils should fork, not all");
        assertTrue(live.stream().anyMatch(TendrilVignette.Tendril::thick)
                && live.stream().anyMatch(tendril -> !tendril.thick()), "some tendrils should root thick, not all");
    }

    @Test
    void everyTendrilWithersAndGoesInItsTime() {
        TendrilVignette vignette = advancedTo(SETTLED);
        Set<TendrilVignette.Tendril> standing = new HashSet<>(vignette.live());
        for (int tick = SETTLED + 1; tick <= SETTLED + TendrilVignette.LONGEST_LIFE_TICKS + 1; tick++) {
            vignette.advance(tick);
        }
        assertTrue(vignette.live().stream().noneMatch(standing::contains), "every tendril should be gone by its life's end");
    }
}
