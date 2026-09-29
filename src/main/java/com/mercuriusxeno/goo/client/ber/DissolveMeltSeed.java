package com.mercuriusxeno.goo.client.ber;

import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * The seed one crucible's dissolving item offsets the dissolve field by, held on the client
 * alone: a melt keeps its seed from its first frame to its last, and a new melt draws a seed
 * other than the last one's, so two melts in a row never erode in the same order (decision
 * diagnose-then-fix-dissolve-repeat). Nothing saves or sends it, so a relog reseeds.
 */
final class DissolveMeltSeed {

    private final RandomSource random;
    private @Nullable Identifier meltingItem;
    private float lastFraction;
    private int seed;

    /**
     * @param random the client random the seeds are drawn from
     */
    DissolveMeltSeed(RandomSource random) {
        this.random = random;
        this.seed = random.nextInt(DissolveGlow.SEED_UNITS);
    }

    /**
     * Returns the seed of the melt the head shows this frame, drawing a fresh one when a new
     * melt begins: the head appearing from none, its item changing, or its fraction falling
     * below the last one seen.
     *
     * @param item     the dissolving item, or null while none dissolves
     * @param fraction the share of it dissolved
     * @return the melt's seed, from 0 to {@link DissolveGlow#SEED_UNITS} less one
     */
    int seedOf(@Nullable Identifier item, float fraction) {
        if (item == null) {
            meltingItem = null;
            return seed;
        }
        if (!item.equals(meltingItem) || fraction < lastFraction) {
            seed = (seed + 1 + random.nextInt(DissolveGlow.SEED_UNITS - 1)) % DissolveGlow.SEED_UNITS;
        }
        meltingItem = item;
        lastFraction = fraction;
        return seed;
    }
}
