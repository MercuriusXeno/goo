package com.mercuriusxeno.goo.client.ber;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A crucible's melt seed: each new melt draws a seed other than the last one's, one melt
 * keeps its seed every frame, and a fresh instance over a melt in progress draws a seed
 * whatever the fraction (decision diagnose-then-fix-dissolve-repeat).
 */
class DissolveMeltSeedTest {

    private static final Identifier LOG = Identifier.withDefaultNamespace("oak_log");
    private static final Identifier PLANKS = Identifier.withDefaultNamespace("oak_planks");
    private static final long RANDOM_SEED = 42L;
    private static final int FRAMES = 20;
    private static final int MELTS = 64;

    /** Plays one melt of the item from whole to gone and returns the seed of its first frame. */
    private static int meltWhole(DissolveMeltSeed meltSeed, Identifier item) {
        int first = meltSeed.seedOf(item, 0f);
        for (int frame = 1; frame <= FRAMES; frame++) {
            meltSeed.seedOf(item, frame / (float) FRAMES);
        }
        return first;
    }

    @Nested
    class NewMelts {

        /** A log dissolving to the end, the head gone, then a second log: the second melt's seed differs. */
        @Test
        void secondMeltAfterAnEmptyHeadDrawsAnotherSeed() {
            DissolveMeltSeed meltSeed = new DissolveMeltSeed(RandomSource.create(RANDOM_SEED));

            int first = meltWhole(meltSeed, LOG);
            meltSeed.seedOf(null, 0f);
            int second = meltSeed.seedOf(LOG, 0f);

            assertNotEquals(first, second);
        }

        /** Melts of one item back to back, each read by its fraction falling, each differ from the one before. */
        @Test
        void consecutiveMeltsNeverRepeatTheLastSeed() {
            DissolveMeltSeed meltSeed = new DissolveMeltSeed(RandomSource.create(RANDOM_SEED));
            Set<Integer> seen = new HashSet<>();

            int previous = meltWhole(meltSeed, LOG);
            seen.add(previous);
            for (int melt = 1; melt < MELTS; melt++) {
                int next = meltWhole(meltSeed, LOG);
                assertNotEquals(previous, next, "melt " + melt);
                assertTrue(next >= 0 && next < DissolveGlow.SEED_UNITS, "seed " + next);
                seen.add(next);
                previous = next;
            }
            assertTrue(seen.size() > 2, "seeds drawn: " + seen);
        }

        /** The head's item changing mid-fraction begins a new melt. */
        @Test
        void anotherItemBeginsAnotherMelt() {
            DissolveMeltSeed meltSeed = new DissolveMeltSeed(RandomSource.create(RANDOM_SEED));

            int log = meltSeed.seedOf(LOG, 0.5f);

            assertNotEquals(log, meltSeed.seedOf(PLANKS, 0.5f));
        }
    }

    /** One melt answers the seed its first frame drew on every frame to its last. */
    @Test
    void oneMeltKeepsItsSeedEveryFrame() {
        DissolveMeltSeed meltSeed = new DissolveMeltSeed(RandomSource.create(RANDOM_SEED));

        int first = meltSeed.seedOf(LOG, 0f);
        for (int frame = 1; frame <= FRAMES; frame++) {
            assertEquals(first, meltSeed.seedOf(LOG, frame / (float) FRAMES), "frame " + frame);
        }
        assertEquals(first, meltSeed.seedOf(LOG, 1f));
    }

    /** A fresh instance, as after a relog, over a head part dissolved draws a seed the fraction does not pick. */
    @Test
    void freshInstanceOverAMeltInProgressDrawsASeed() {
        int atSixTenths = new DissolveMeltSeed(RandomSource.create(RANDOM_SEED)).seedOf(LOG, 0.6f);
        int atOneTenth = new DissolveMeltSeed(RandomSource.create(RANDOM_SEED)).seedOf(LOG, 0.1f);

        assertEquals(atOneTenth, atSixTenths);
        assertTrue(atSixTenths >= 0 && atSixTenths < DissolveGlow.SEED_UNITS);
    }
}
