package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.VinesStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.NonNull;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Leaf's Vines cell: the volume cell bound to Vines' goo, which on each
 * scheduled tick grows a goologged vine on one wall its goo coats, wherever a
 * vine could validly hang, the vine standing in the cell's own space, and
 * spends the layers a vine costs; it keeps growing while a valid wall and
 * goo remain, so a heavy spray saturates a wall in vines, and keeps its
 * layers once every coated wall bears one. Goo spent to nothing, by growing
 * or by a hit eating its last layer, leaves its vines hanging alone, and a
 * cell bearing a vine climbs as a vine does.
 * vines-goo-on-walls-places-goologged-vines-and-depletes
 */
public class VinesCellBlock extends VolumeCellBlock {

    /**
     * Creates a Vines cell.
     *
     * @param properties the block properties
     * @param binding    the Vines binding
     */
    public VinesCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
        BlockState unplanted = defaultBlockState();
        for (Direction side : GoologgedSides.SIDES) {
            unplanted = unplanted.setValue(GoologgedSides.plantProperty(side), false);
        }
        registerDefaultState(unplanted);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GoologgedSides.NORTH, GoologgedSides.EAST, GoologgedSides.SOUTH, GoologgedSides.WEST);
    }

    /**
     * Pours as any cell does, then, where the cell still stands, grows its
     * next vine.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        BlockState poured = level.getBlockState(pos);
        if (poured.is(this)) {
            VinesStep.in(layingProgram(level)).ifPresent(vines -> growVine(level, pos, poured, vines.layersPerVine()));
        }
    }

    /**
     * Climbs as a vine does where a vine hangs in the cell.
     * vines-goo-on-walls-places-goologged-vines-and-depletes
     */
    @Override
    public boolean isLadder(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos,
                            @NonNull LivingEntity entity) {
        return GoologgedSides.SIDES.stream().anyMatch(side -> GoologgedSides.plantedOn(state, side));
    }

    /**
     * Grows one goologged vine on the next coated wall a vine can hang on,
     * spending its layers: the cell stands anew with the vine, which ticks it
     * again for the next, or, spent to nothing, leaves its vines as a vine.
     *
     * @param level    the server level
     * @param pos      the cell
     * @param state    the cell's state after its pour
     * @param perVine  the layers a vine costs
     */
    private void growVine(ServerLevel level, BlockPos pos, BlockState state, int perVine) {
        List<Direction> coated = GoologgedSides.SIDES.stream()
                .filter(side -> coatOn(state, side) == SideCoat.ATTACHED)
                .filter(side -> VineBlock.isAcceptableNeighbour(level, pos.relative(side), side)).toList();
        Set<Direction> planted = GoologgedSides.SIDES.stream().filter(side -> GoologgedSides.plantedOn(state, side))
                .collect(Collectors.toSet());
        GoologgedSides.Planting planting = GoologgedSides.nextPlanting(coated, planted, state.getValue(LAYERS),
                perVine);
        planting.side().ifPresent(side -> {
            BlockState grown = state.setValue(GoologgedSides.plantProperty(side), true);
            level.setBlock(pos, planting.layersLeft() > 0 ? grown.setValue(LAYERS, planting.layersLeft())
                    : spent(grown), UPDATE_ALL);
        });
    }

    /**
     * What a Vines cell leaves once its goo is spent: its vines hanging alone
     * as a vine, or air where it bore none.
     *
     * @param cell the cell's state
     * @return a vine hanging on every side the cell's vines hung on, or air
     */
    public static BlockState spent(BlockState cell) {
        if (GoologgedSides.SIDES.stream().noneMatch(side -> GoologgedSides.plantedOn(cell, side))) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState vine = Blocks.VINE.defaultBlockState();
        for (Direction side : GoologgedSides.SIDES) {
            vine = vine.setValue(VineBlock.getPropertyForFace(side), GoologgedSides.plantedOn(cell, side));
        }
        return vine;
    }
}
