package com.mercuriusxeno.goo.ability;

/**
 * Pure-function placement decision for ability-block and frost-field world
 * effects. Given the state of the two candidate positions (the hit block and
 * the face-adjacent block) and how the effect block reacts to water, returns
 * the action to take.
 *
 * <p>For each candidate in order, the hit block first: water handling,
 * lava skip, or displace into air / non-fluid replaceable. Anything liquid
 * other than through explicit water handling is never displaced, and a
 * standing marker is never stacked onto, so a second throw lands beside it
 * (decision splat-runs-the-program-no-fuse).</p>
 *
 * <p>This class is Minecraft-free so the decision matrix can be unit-tested
 * without bootstrapping a level. Translation between real
 * {@link net.minecraft.world.level.block.state.BlockState} instances and
 * {@link CandidateState} lives in the caller.</p>
 */
public final class ChainPlacementRules {

    private static final Decision NONE_DECISION = new Decision(Action.NONE, -1);

    /** How an effect block reacts when a candidate position contains water. */
    public enum WaterHandling {
        /** Block implements SimpleWaterloggedBlock: place waterlogged at the candidate. */
        WATERLOG,
        /** Frost field special: freeze the water to ice, then place the field at candidate.above(). */
        FREEZE_AND_RISE,
        /** Block has no water support: water causes the candidate to fall through. */
        NONE
    }

    /** Action the runtime should take for the decided candidate. */
    public enum Action {
        /** Place a fresh marker, displacing whatever was there. */
        DISPLACE,
        /** Place a fresh marker with WATERLOGGED=true; preserves the water fluid. */
        WATERLOG,
        /** Freeze the candidate water to ice, then place the marker at candidate.above(). */
        FREEZE_AND_RISE,
        /** Nothing can be placed. */
        NONE
    }

    /**
     * Decision output: the action and which candidate it applies to.
     *
     * @param action         the action to perform
     * @param candidateIndex 0 for the hit block, 1 for the face-adjacent block, -1 for NONE
     */
    public record Decision(Action action, int candidateIndex) {}

    /**
     * Summary of a candidate block position relevant to placement.
     *
     * <p>All fields are observable from a {@code BlockState} plus the world.
     * Treat these as independent flags - the caller supplies them without
     * relying on which combinations are actually possible in vanilla.</p>
     *
     * @param isAir            true if the block is minecraft:air
     * @param isReplaceable    true if {@code BlockState.canBeReplaced()} returns true
     * @param hasWater         true if the block's fluid state is water
     * @param hasLava          true if the block's fluid state is lava
     * @param aboveIsPlaceable true if the block at candidate.above() is air or non-fluid replaceable;
     *                         only consulted when {@link WaterHandling#FREEZE_AND_RISE} is in use
     */
    public record CandidateState(
            boolean isAir,
            boolean isReplaceable,
            boolean hasWater,
            boolean hasLava,
            boolean aboveIsPlaceable) {}

    private ChainPlacementRules() {}

    /**
     * Decides the placement action for the given candidates.
     *
     * @param hit      state of the hit block (candidate index 0)
     * @param adjacent state of the face-adjacent block (candidate index 1)
     * @param handling how this block type reacts to water at a candidate
     * @return the decided action and chosen candidate index
     */
    public static Decision decide(CandidateState hit, CandidateState adjacent, WaterHandling handling) {
        Decision atHit = tryPlaceAt(hit, 0, handling);
        if (atHit.action() != Action.NONE) { return atHit; }
        return tryPlaceAt(adjacent, 1, handling);
    }

    /**
     * Computes the placement decision for a single candidate.
     *
     * @param c        the candidate's state
     * @param idx      the candidate index (0 for hit, 1 for adjacent)
     * @param handling how this block type reacts to water at a candidate
     * @return a non-NONE decision if this candidate accepts placement, else NONE
     */
    private static Decision tryPlaceAt(CandidateState c, int idx, WaterHandling handling) {
        if (c.hasWater()) { return resolveWater(c, idx, handling); }
        if (c.hasLava()) { return NONE_DECISION; }
        if (c.isAir() || c.isReplaceable()) { return new Decision(Action.DISPLACE, idx); }
        return NONE_DECISION;
    }

    /**
     * Applies the water-handling rule for a candidate that contains water.
     *
     * @param c        the candidate's state
     * @param idx      the candidate index
     * @param handling the water handling mode
     * @return waterlog / freeze-and-rise / none as appropriate
     */
    private static Decision resolveWater(CandidateState c, int idx, WaterHandling handling) {
        return switch (handling) {
            case WATERLOG -> new Decision(Action.WATERLOG, idx);
            case FREEZE_AND_RISE -> c.aboveIsPlaceable()
                    ? new Decision(Action.FREEZE_AND_RISE, idx)
                    : NONE_DECISION;
            case NONE -> NONE_DECISION;
        };
    }
}
