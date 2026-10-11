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
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        return between(level, hit, hit.relative(face));
    }

    /**
     * Decides where a blob striking a block face at a point lands: the struck
     * block or the cell beyond the face, and where neither takes it, as on
     * the side of a fence arm looking onto the next fence block, the struck
     * block's free neighbor nearest the point, so a blob striking a thin
     * block's side still lands.
     *
     * @param level the current level
     * @param hit   the struck block
     * @param face  the struck face
     * @param point the point the blob struck
     * @return the landing, or empty when no neighbor of the struck block takes it
     */
    public static Optional<LandingSpot> resolve(Level level, BlockPos hit, Direction face, Vec3 point) {
        Optional<LandingSpot> spot = resolve(level, hit, face);
        if (spot.isPresent()) {
            return spot;
        }
        List<Direction> nearestFirst = new ArrayList<>(List.of(Direction.values()));
        nearestFirst.sort(Comparator.comparingDouble(side -> Vec3.atCenterOf(hit.relative(side)).distanceToSqr(point)));
        for (Direction side : nearestFirst) {
            Optional<LandingSpot> beside = between(level, hit, hit.relative(side));
            if (beside.isPresent()) {
                return beside;
            }
        }
        return Optional.empty();
    }

    /**
     * The lava cell a blob that cools lava lands in, where the placement
     * rules refused the landing: the struck block where it holds lava, the
     * cell on the struck face otherwise, so a throw through a lava pool
     * striking its floor lands in the lava above it.
     * weird-bounces-and-softens-harm
     *
     * @param level the current level
     * @param hit   the struck block
     * @param face  the struck face
     * @return the lava landing, or empty when neither candidate holds lava
     */
    public static Optional<LandingSpot> inLava(Level level, BlockPos hit, Direction face) {
        BlockPos adjacent = hit.relative(face);
        if (level.getFluidState(hit).is(Fluids.LAVA)) {
            return Optional.of(new LandingSpot(hit, false));
        }
        return level.getFluidState(adjacent).is(Fluids.LAVA)
                ? Optional.of(new LandingSpot(adjacent, false)) : Optional.empty();
    }

    /**
     * Decides between the struck block and one cell beside it.
     *
     * @param level    the current level
     * @param hit      the struck block
     * @param adjacent the cell beside it
     * @return the landing, or empty when neither candidate takes it
     */
    private static Optional<LandingSpot> between(Level level, BlockPos hit, BlockPos adjacent) {
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
