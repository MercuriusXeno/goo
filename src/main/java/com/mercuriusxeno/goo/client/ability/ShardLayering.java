package com.mercuriusxeno.goo.client.ability;

/**
 * Where a razor shard draws as the prism dome grows past it: undrawn until
 * the dome reaches nine tenths of the shard's resting distance, born there
 * fully transparent, then drifting the last tenth to rest while it fades in
 * as the dome passes, at rest at full alpha once the dome has passed. As the
 * dome travels outward the shards appear in layers, drifting to rest.
 * decision razor-shards-appear-in-layers-ahead-of-the-dome
 */
final class ShardLayering {

    /** The share of its resting distance a shard is born at. */
    static final float BIRTH_SHARE = 0.9f;

    private ShardLayering() {
    }

    /**
     * What a shard draws this frame.
     *
     * @param drawn    whether the shard draws at all
     * @param distance the shard's distance from the cloud's center, in blocks
     * @param alpha    the shard's opacity share, 0 fully transparent to 1 full
     */
    record ShardReveal(boolean drawn, float distance, float alpha) {

        /** A shard the dome has not reached. */
        static final ShardReveal UNDRAWN = new ShardReveal(false, 0f, 0f);
    }

    /**
     * Reveals a shard against the dome's current radius.
     *
     * @param domeRadius      the dome's current radius, in blocks
     * @param restingDistance the shard's resting distance from the cloud's center, in blocks
     * @return what the shard draws this frame
     */
    static ShardReveal reveal(float domeRadius, float restingDistance) {
        float birth = BIRTH_SHARE * restingDistance;
        if (domeRadius < birth) {
            return ShardReveal.UNDRAWN;
        }
        if (domeRadius >= restingDistance) {
            return new ShardReveal(true, restingDistance, 1f);
        }
        float drifted = (domeRadius - birth) / (restingDistance - birth);
        return new ShardReveal(true, birth + drifted * (restingDistance - birth), drifted);
    }

    /**
     * The prism dome's radius a number of ticks after the landing: the
     * burnout's shell radius over its progress, so the shards layer on the
     * dome the crystal burnout draws.
     *
     * @param ticksSinceLanding the ticks since the blob landed, partial tick included
     * @param reach             the cloud's full radius, in blocks
     * @return the dome's radius, in blocks
     */
    static float domeRadius(float ticksSinceLanding, float reach) {
        float progress = Math.min(1f, Math.max(0f, ticksSinceLanding / CrystalExplosionVisual.DURATION_TICKS));
        return CrystalExplosionVisual.shellRadius(progress, reach);
    }
}
