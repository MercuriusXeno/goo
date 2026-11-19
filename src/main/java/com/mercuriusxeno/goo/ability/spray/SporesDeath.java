package com.mercuriusxeno.goo.ability.spray;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.SporesStep;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.block.ability.SporesCellBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * What a death Spores goo feeds does besides the spore burst: it turns each
 * grass block, dirt and coarse dirt within the burst's radius to mycelium and
 * changes no other block; and a mob that never stood on the goo, dying within
 * a cell's death reach, is fed by that cell, its burst crediting the cell's
 * placer. The death spends none of the goo's layers.
 * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium
 */
public final class SporesDeath {

    private static final double HALF = 0.5;

    private SporesDeath() {
    }

    /**
     * A Spores cell feeding a death that carried no spores: the nearest one
     * within the step's death reach, with its step and the player it credits.
     *
     * @param cell     the cell
     * @param spores   Spores' step
     * @param creditor the player the burst credits, or null for none
     */
    public record Feeder(BlockPos cell, SporesStep spores, @Nullable ServerPlayer creditor) {
    }

    /**
     * Whether a block turns to mycelium under a death Spores goo fed: grass block, dirt and coarse dirt alone.
     * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium
     *
     * @param state the block
     * @return true for a grass block, dirt or coarse dirt
     */
    public static boolean turnsToMycelium(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT);
    }

    /**
     * Turns each grass block, dirt and coarse dirt whose top lies within a radius of a death to mycelium.
     *
     * @param level  the server level
     * @param center where the mob fell
     * @param radius the burst's radius
     */
    public static void growMycelium(ServerLevel level, Vec3 center, double radius) {
        int reach = (int) Math.ceil(radius);
        BlockPos fell = BlockPos.containing(center);
        for (BlockPos pos : BlockPos.betweenClosed(fell.offset(-reach, -reach, -reach), fell.offset(reach, reach, reach))) {
            // the ground a corpse lies on counts from its top face, where the corpse fell
            if (Vec3.atCenterOf(pos).add(0, HALF, 0).distanceTo(center) <= radius
                    && turnsToMycelium(level.getBlockState(pos))) {
                level.setBlock(pos, Blocks.MYCELIUM.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * The Spores cell nearest a death within the step's death reach, where Spores ships.
     *
     * @param level the server level
     * @param fell  the block the mob fell in
     * @return the feeding cell, empty where none stands in reach
     */
    public static Optional<Feeder> feederNear(ServerLevel level, BlockPos fell) {
        Optional<SporesStep> step = Optional.ofNullable(AbilityRegistry.of(level).getAbility(CellBinding.SPORES_ABILITY))
                .map(AbilityDefinition::behaviors).flatMap(SporesStep::in);
        if (step.isEmpty()) {
            return Optional.empty();
        }
        SporesCellBlock cell = GooBlocks.SCULK_SPORES.get();
        int reach = step.get().deathReach();
        for (BlockPos pos : BlockPos.withinManhattan(fell, reach, reach, reach)) {
            if (pos.distSqr(fell) <= (double) reach * reach && level.getBlockState(pos).is(cell)) {
                BlockPos found = pos.immutable();
                return Optional.of(new Feeder(found, step.get(), cell.creditedPlayer(level, found)));
            }
        }
        return Optional.empty();
    }
}
