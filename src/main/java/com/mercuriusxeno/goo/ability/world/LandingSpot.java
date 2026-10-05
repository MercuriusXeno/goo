package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.ChainPlacementRules;
import com.mercuriusxeno.goo.ability.ChainPlacementRules.CandidateState;
import com.mercuriusxeno.goo.ability.ChainPlacementRules.Decision;
import com.mercuriusxeno.goo.ability.ChainPlacementRules.WaterHandling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import java.util.Optional;

/**
 * The cell a blob landing on a block lands in, decided through
 * {@link ChainPlacementRules} and placing nothing: a replaceable or watery
 * struck block takes the landing in place, lava refuses it, a solid or
 * standing block passes it to the cell on the struck face, so a throw at a
 * standing ability block lands beside it (decisions
 * ability-path-uses-placement-rules, splat-runs-the-program-no-fuse).
 *
 * @param cell        the cell the blob lands in
 * @param waterlogged whether the cell holds water a standing block keeps
 */
public record LandingSpot(BlockPos cell, boolean waterlogged) {

    /**
     * Decides where a blob striking a block face lands.
     *
     * @param level the current level
     * @param hit   the struck block
     * @param face  the struck face
     * @return the landing, or empty when neither candidate takes it
     */
    public static Optional<LandingSpot> resolve(Level level, BlockPos hit, Direction face) {
        BlockPos adjacent = hit.relative(face);
        Decision decision = ChainPlacementRules.decide(candidate(level, hit), candidate(level, adjacent),
                WaterHandling.WATERLOG);
        BlockPos cell = decision.candidateIndex() == 0 ? hit : adjacent;
        return switch (decision.action()) {
            case DISPLACE -> Optional.of(new LandingSpot(cell, false));
            case WATERLOG -> Optional.of(new LandingSpot(cell, true));
            case FREEZE_AND_RISE, NONE -> Optional.empty();
        };
    }

    /**
     * Reads a candidate cell's state for the placement rules.
     *
     * @param level the current level
     * @param pos   the candidate position
     * @return the candidate state snapshot
     */
    private static CandidateState candidate(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        return new CandidateState(state.isAir(), state.canBeReplaced(), fluid.is(Fluids.WATER),
                fluid.is(Fluids.LAVA), false);
    }
}
