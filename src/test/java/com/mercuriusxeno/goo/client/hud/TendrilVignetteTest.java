package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.client.ability.SculkerSenses;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vignette's tendrils are a live population, not a fixed set: they
 * sprout at random times into evenly spaced slots along the edges, many at
 * once, each with its own length, thickness and wandering path that may
 * fork, and each withers and goes in its time, so the count on screen drifts
 * and no layout repeats; and a soft fringe fills every edge behind them, its
 * inner edge a few slow, drifting sine waves that undulate and never break (decisions sight-lengthens-shift-and-outlines-fungus and
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
    void noTwoTendrilsSproutOnTopOfEachOther() {
        TendrilVignette vignette = new TendrilVignette(SEED);
        for (int tick = 0; tick <= SAMPLES_UNTIL; tick++) {
            vignette.advance(tick);
            List<TendrilVignette.Tendril> live = vignette.live();
            for (int one = 0; one < live.size(); one++) {
                for (int other = one + 1; other < live.size(); other++) {
                    TendrilVignette.Tendril a = live.get(one);
                    TendrilVignette.Tendril b = live.get(other);
                    assertFalse(a.edge() == b.edge() && Math.abs(a.along() - b.along()) < TendrilVignette.ROOT_GAP,
                            "two tendrils rooted within the gap of each other on one edge at tick " + tick);
                }
            }
        }
    }

    @Test
    void everyTendrilHeadsMostlyInwardFromItsEdge() {
        for (TendrilVignette.Tendril tendril : advancedTo(SAMPLES_UNTIL).live()) {
            float[] path = tendril.path();
            for (int step = 0; step < path.length; step++) {
                float inward = (float) step / (path.length - 1);
                assertTrue(Math.abs(path[step]) <= TendrilVignette.MOST_SLANT * inward + 1e-6f,
                        "a tendril strayed " + path[step] + " sideways " + inward + " of the way in");
            }
        }
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
    void tendrilsRootInEvenlySpacedSlotsOnePerSlot() {
        TendrilVignette vignette = new TendrilVignette(SEED);
        for (int tick = 0; tick <= SAMPLES_UNTIL; tick++) {
            vignette.advance(tick);
            Set<Integer> slots = new HashSet<>();
            for (TendrilVignette.Tendril tendril : vignette.live()) {
                int slot = TendrilVignette.slotOf(tendril.along());
                float middle = (slot + 0.5f) / TendrilVignette.SLOTS_PER_EDGE;
                assertTrue(Math.abs(tendril.along() - middle) <= TendrilVignette.SLOT_JITTER + 1e-6f,
                        "a tendril rooted " + tendril.along() + ", off its slot's middle " + middle);
                assertTrue(slots.add(tendril.edge() * TendrilVignette.SLOTS_PER_EDGE + slot),
                        "two tendrils rooted in one slot at tick " + tick);
            }
        }
    }

    @Test
    void mostSlotsStandFilledOnceSettled() {
        TendrilVignette vignette = new TendrilVignette(SEED);
        int samples = 0;
        int standing = 0;
        for (int tick = 0; tick <= SAMPLES_UNTIL; tick++) {
            vignette.advance(tick);
            if (tick >= SETTLED && tick % SAMPLE_EVERY == 0) {
                samples++;
                standing += vignette.live().size();
            }
        }
        float filled = (float) standing / samples / (TendrilVignette.SLOTS_PER_EDGE * 4);
        assertTrue(filled >= 0.75f, "most slots should stand filled, " + filled + " do");
    }

    @Test
    void aSoftFringeUndulatesAlongEveryEdgeAndNeverBreaks() {
        TendrilVignette vignette = new TendrilVignette(SEED);
        for (int edge = 0; edge < 4; edge++) {
            float lowest = Float.MAX_VALUE;
            float highest = 0f;
            for (int time = 0; time <= SAMPLES_UNTIL; time += SAMPLE_EVERY) {
                for (int step = 0; step <= 100; step++) {
                    float depth = vignette.fringeDepth(edge, step / 100f, time);
                    lowest = Math.min(lowest, depth);
                    highest = Math.max(highest, depth);
                }
            }
            assertTrue(lowest > 0f, "the fringe should never break along edge " + edge + ", it fell to " + lowest);
            assertTrue(highest <= TendrilVignette.DEEPEST_FRINGE + 1e-6f, "the fringe reached too deep, " + highest);
            assertTrue(highest - lowest >= TendrilVignette.FRINGE_BASE / 4,
                    "the fringe should undulate along edge " + edge + ", it spans " + lowest + " to " + highest);
        }
    }

    /**
     * Sculker's vignette grows a vein network in place of tendrils: thin
     * veins forking into a web, rooted in uneven clumps with bare rim
     * between, in a teal near the mist's (decisions
     * sculker-tendrils-are-a-branching-vein-network,
     * sculker-veins-root-in-clusters-and-bare-gaps and
     * sculker-tendrils-blend-into-the-teal-haze).
     */
    @Nested
    class SculkerVeins {

        private static final float CLUMP_SPAN = 0.06f;
        private static final int CLUMP_ROOTS = 3;
        private static final float BARE_OVER_MEAN = 3f;

        private static TendrilVignette veinedTo(int ticks) {
            TendrilVignette vignette = TendrilVignette.veined(SEED);
            for (int tick = 0; tick <= ticks; tick++) {
                vignette.advance(tick);
            }
            return vignette;
        }

        @Test
        void veinsForkAndTheirForksForkAgainEachOneThinLine() {
            List<VeinNetwork.Vein> veins = veinedTo(SETTLED).veins();
            assertTrue(veins.stream().anyMatch(vein -> vein.deepestFork() >= 2),
                    "some vein should hold a fork that itself forks");
            int widest = veins.stream().flatMap(vein -> vein.branches().stream())
                    .mapToInt(VeinNetwork.Branch::stroke).max().orElse(Integer.MAX_VALUE);
            assertEquals(VeinNetwork.THIN_STROKE, widest, "every vein should draw one thin line wide");
            assertEquals(1, VeinNetwork.THIN_STROKE, "the thin stroke is one pixel");
        }

        @Test
        void veinsRootInUnevenClumpsWithBareRimBetween() {
            List<Float> gaps = new ArrayList<>();
            boolean clumped = false;
            List<VeinNetwork.Vein> veins = veinedTo(SETTLED).veins();
            for (int edge = 0; edge < 4; edge++) {
                int onEdge = edge;
                List<Float> roots = veins.stream().filter(vein -> vein.edge() == onEdge)
                        .map(VeinNetwork.Vein::along).sorted().toList();
                for (int root = 1; root < roots.size(); root++) {
                    gaps.add(roots.get(root) - roots.get(root - 1));
                }
                for (int root = CLUMP_ROOTS - 1; root < roots.size(); root++) {
                    clumped |= roots.get(root) - roots.get(root - CLUMP_ROOTS + 1) <= CLUMP_SPAN;
                }
            }
            double mean = gaps.stream().mapToDouble(Float::doubleValue).average().orElse(0);
            double widest = gaps.stream().mapToDouble(Float::doubleValue).max().orElse(0);
            assertTrue(clumped, "some stretch of rim should hold " + CLUMP_ROOTS + " roots close together");
            assertTrue(mean > 0 && widest >= BARE_OVER_MEAN * mean,
                    "some stretch should stand bare, " + widest + " against a mean gap of " + mean);
        }

        @Test
        void veinsDrawNearerTheMistsTealThanSculkTeal() {
            int mist = SculkerVignetteLayer.LOOK.mistRgb();
            assertTrue(rgbDistance(SculkerVignetteLayer.LOOK.tendrilRgb(), mist)
                    < rgbDistance(SculkerSenses.SCULK_RGB, mist), "the veins should sit inside the haze");
        }

        private static double rgbDistance(int one, int other) {
            int red = (one >> 16 & 0xFF) - (other >> 16 & 0xFF);
            int green = (one >> 8 & 0xFF) - (other >> 8 & 0xFF);
            int blue = (one & 0xFF) - (other & 0xFF);
            return Math.sqrt(red * red + green * green + blue * blue);
        }
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
