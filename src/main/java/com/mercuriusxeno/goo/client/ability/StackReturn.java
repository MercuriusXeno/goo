package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.crystal.StackHover;
import java.util.Map;

/**
 * Released weapons returning to the palm blob, the inverse of their forming:
 * on release each held knife or javelin turns back into its cutaway cuboid
 * and travels back into the blob, one starting every 2 ticks in the reverse
 * of the order they formed, each taking 5 ticks from its start to its merge.
 * The weapon's shape eases back to the cuboid as it leaves its slot, so no
 * weapon reaches the blob fully formed, and the blob regains each cuboid's
 * share by ease as it merges.
 * decision released-weapons-turn-back-into-blobs-that-rejoin-the-palm-blob
 */
final class StackReturn {

    /** Ticks between one weapon's return starting and the next's. */
    static final double START_SPACING = 2;
    /** Ticks one weapon takes from its start to merging with the blob. */
    static final double RETURN_TICKS = 5;
    /** How far into its return a cuboid reaches the blob and begins to sink into it. */
    private static final float MERGE_FROM = 0.6f;

    private StackReturn() {
    }

    /**
     * A release: the count let go and the game time it was.
     *
     * @param released the count released
     * @param at       the game time of the release
     */
    record Released(int released, long at) {

        /**
         * Whether every weapon released has merged with the blob by a time.
         *
         * @param now the game time, with the partial tick
         * @return true once the last has merged
         */
        boolean doneAt(double now) {
            return now - at >= startOf(0, released) + RETURN_TICKS;
        }
    }

    /**
     * Notes a release where a count fell because the hold ended: every
     * weapon held returns to the blob; a count that fell to a throw, the last
     * weapon thrown included, starts none.
     * decision released-weapons-turn-back-into-blobs-that-rejoin-the-palm-blob
     *
     * @param returns  each caster's return, by entity id
     * @param casterId the caster's entity id
     * @param before   the count held before
     * @param formed   the count held now
     * @param now      the game time
     * @param released whether the count fell because the hold ended
     */
    static void noteRelease(Map<Integer, Released> returns, int casterId, int before, int formed, long now,
                            boolean released) {
        if (released && formed <= 0 && before > 0) {
            returns.put(casterId, new Released(before, now));
        }
    }

    /**
     * The ticks after a release a weapon's return starts: the last formed at
     * the release, each earlier one 2 ticks after the one formed after it.
     *
     * @param index    the weapon's index, 0 the first formed
     * @param released the count released
     * @return the start, in ticks after the release
     */
    static double startOf(int index, int released) {
        return (released - 1 - index) * START_SPACING;
    }

    /**
     * How far a released weapon's return has run.
     *
     * @param index    the weapon's index, 0 the first formed
     * @param released the count released
     * @param since    ticks since the release, with the partial tick
     * @return 0 before it starts to 1 once merged
     */
    static float progressAt(int index, int released, double since) {
        return (float) Math.clamp((since - startOf(index, released)) / RETURN_TICKS, 0.0, 1.0);
    }

    /**
     * A returning weapon's shape weight: whole as it leaves its slot, falling
     * by ease to none as it merges, its cuboid taking the rest.
     *
     * @param progress how far its return has run, 0 to 1
     * @return the weapon's weight, 1 whole to 0 a cuboid
     */
    static float weaponWeightAt(float progress) {
        return 1f - travelAt(progress);
    }

    /**
     * How far a returning weapon has travelled from its slot to the blob's middle, eased in and out.
     *
     * @param progress how far its return has run, 0 to 1
     * @return the share of the way, 0 to 1
     */
    static float travelAt(float progress) {
        return HoveringKnives.easedOver(progress, 1);
    }

    /**
     * The share of the blob the released weapons have still to bring back to
     * it: each one's share, carried by its cuboid on the way and given to the
     * blob by ease as the cuboid sinks into it, all of it once merged.
     *
     * @param release the release
     * @param stack   the most the hold stacks
     * @param now     the game time, with the partial tick
     * @return the share still out, 0 to 1
     */
    static double outstandingShareOf(Released release, int stack, double now) {
        double outstanding = 0;
        for (int index = 0; index < release.released(); index++) {
            float progress = progressAt(index, release.released(), now - release.at());
            outstanding += StackHover.shareOf(stack) * (1 - HoveringKnives.easedOver(progress - MERGE_FROM,
                    1 - MERGE_FROM));
        }
        return outstanding;
    }
}
