package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.ChainPlacementRules;
import com.mercuriusxeno.goo.ability.ChainPlacementRules.CandidateState;
import com.mercuriusxeno.goo.ability.ChainPlacementRules.Decision;
import com.mercuriusxeno.goo.ability.ChainPlacementRules.WaterHandling;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlock;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * On-hit placement of an ability's chain marker. Builds a
 * {@link ChainPlacementRules} candidate state for the hit and
 * face-adjacent positions, applies the decision, and initializes the
 * resulting marker from the ability.
 *
 * <p>Placement rule: stack onto an existing marker of the same ability
 * first, then try the hit block, then the face-adjacent block. The hit
 * block is a first-class placement target (fire, tall grass, snow, water,
 * etc.), so non-solid targets do not always push the marker one block
 * off the face.</p>
 */
public final class EffectBlockPlacement {

    /**
     * Block update flags for setBlock calls.
     */
    private static final int BLOCK_UPDATE_FLAGS = 3;
    /**
     * Fallback face used when the hit direction is unknown.
     */
    private static final Direction DEFAULT_FACE = Direction.UP;

    private EffectBlockPlacement() {
    }

    /**
     * Computes and applies the placement decision for a chain marker at the
     * hit block or the face-adjacent block.
     *
     * @param level    the current level
     * @param hitBlock the hit block position
     * @param face     the face that was hit, or null
     * @param kind     which markers stack with this one, and how a fresh one initializes
     */
    private static void placeChainMarker(Level level, BlockPos hitBlock,
                                         @Nullable Direction face, MarkerKind kind) {
        Direction resolvedFace = face == null ? DEFAULT_FACE : face;
        BlockPos adjacentPos = hitBlock.relative(resolvedFace);

        CandidateState hitState = chainCandidateState(level, hitBlock, kind);
        CandidateState adjacentState = chainCandidateState(level, adjacentPos, kind);
        Decision decision = ChainPlacementRules.decide(hitState, adjacentState, WaterHandling.WATERLOG);
        applyChainDecision(level, decision, hitBlock, adjacentPos, kind, resolvedFace);
    }

    /**
     * Builds a CandidateState for chain marker placement. The aboveIsPlaceable
     * field is unused for WATERLOG handling so is left false.
     *
     * @param level the current level
     * @param pos   the candidate position
     * @param kind  the marker kind, for same-marker stack detection
     * @return the candidate state snapshot
     */
    private static CandidateState chainCandidateState(Level level, BlockPos pos, MarkerKind kind) {
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        return new CandidateState(
                isExistingChainMarker(level, pos, state, kind),
                state.isAir(),
                state.canBeReplaced(),
                fluid.is(Fluids.WATER),
                fluid.is(Fluids.LAVA),
                false);
    }

    /**
     * Returns true if the block at {@code pos} is an existing chain marker
     * this kind stacks onto.
     *
     * @param level the current level
     * @param pos   the position to test
     * @param state the block state at {@code pos}
     * @param kind  the marker kind
     * @return true if a matching chain marker is present
     */
    private static boolean isExistingChainMarker(Level level, BlockPos pos, BlockState state, MarkerKind kind) {
        return state.is(GooBlocks.CHAIN_MARKER.get())
                && level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be
                && kind.stacksOnto().test(be);
    }

    /**
     * Dispatches a chain-marker placement decision to the right mutation.
     *
     * @param level    the current level
     * @param decision the decision to apply
     * @param hitPos   the hit block position
     * @param adjPos   the face-adjacent block position
     * @param kind     the marker kind
     * @param face     the resolved hit face
     */
    private static void applyChainDecision(Level level, Decision decision,
                                           BlockPos hitPos, BlockPos adjPos, MarkerKind kind, Direction face) {
        BlockPos target = pickCandidate(decision, hitPos, adjPos);
        switch (decision.action()) {
            case STACK -> stackChainMarker(level, target);
            case DISPLACE -> placeFreshChainMarker(level, target, kind, face, false);
            case WATERLOG -> placeFreshChainMarker(level, target, kind, face, true);
            case FREEZE_AND_RISE, NONE -> { /* no-op */ }
        }
    }

    /**
     * Bumps the stack count on an existing chain marker at {@code pos}.
     *
     * @param level the current level
     * @param pos   the marker position
     */
    private static void stackChainMarker(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be) {
            be.tryStack();
        }
    }

    /**
     * Places a new chain marker at {@code pos}, optionally waterlogged, and
     * initializes its block entity through the kind.
     *
     * @param level       the current level
     * @param pos         the placement position
     * @param kind        the marker kind
     * @param face        the hit face direction
     * @param waterlogged whether the marker should coexist with a water fluid
     */
    private static void placeFreshChainMarker(Level level, BlockPos pos, MarkerKind kind,
                                              Direction face, boolean waterlogged) {
        BlockState markerState = GooBlocks.CHAIN_MARKER.get().defaultBlockState()
                .setValue(ChainMarkerBlock.WATERLOGGED, waterlogged);
        level.setBlock(pos, markerState, BLOCK_UPDATE_FLAGS);
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be) {
            kind.init().accept(be, face);
        }
    }


    /**
     * Resolves the candidate position for a decision (index 0 = hit, 1 = adjacent).
     *
     * @param decision the decision
     * @param hitPos   the hit block position
     * @param adjPos   the face-adjacent block position
     * @return the chosen position
     */
    private static BlockPos pickCandidate(Decision decision, BlockPos hitPos, BlockPos adjPos) {
        return decision.candidateIndex() == 0 ? hitPos : adjPos;
    }

    /**
     * Places or stacks a chain marker for a data-driven ability, deciding
     * through {@link ChainPlacementRules}: a replaceable hit block takes the
     * marker in place, water waterlogs it, lava refuses it, and a blob stacks
     * only onto a marker of the same ability (decision
     * ability-path-uses-placement-rules).
     *
     * @param level   the server level
     * @param pos     the target block position
     * @param type    the goo type
     * @param face    the target face
     * @param ability the ability definition
     */
    public static void placeOrStackAbility(ServerLevel level, BlockPos pos,
                                           ResourceKey<GooTypeDefinition> type, Direction face,
                                           AbilityDefinition ability) {
        placeChainMarker(level, pos, face, MarkerKind.ofAbility(type, ability));
    }

    /**
     * Which standing markers a blob stacks onto, and how a fresh marker it
     * places initializes.
     *
     * @param stacksOnto true for a marker this blob stacks onto
     * @param init       initializes a freshly placed marker with its placed face
     */
    private record MarkerKind(Predicate<ChainMarkerBlockEntity> stacksOnto,
                              BiConsumer<ChainMarkerBlockEntity, Direction> init) {

        /**
         * An ability marker, stacking onto a marker carrying the same ability id.
         *
         * @param type    the goo type
         * @param ability the ability
         * @return the kind
         */
        static MarkerKind ofAbility(ResourceKey<GooTypeDefinition> type, AbilityDefinition ability) {
            String abilityId = ability.id().toString();
            return new MarkerKind(be -> abilityId.equals(be.getAbilityId()),
                    (be, face) -> be.initChainFromAbility(type, face, ability));
        }
    }
}
