package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Weird's slime coat: a thin glossy green film laid on a block's top like a
 * snow layer, the block under it unchanged and showing through, drips
 * running down that block's sides at the coat's open edges. It stands until
 * something destroys it and drops nothing.
 * coat-is-a-green-slime-film-over-the-blocks-own-texture
 */
public class SlimeCoatBlock extends Block {

    /** Whether the film drips down the north side of the block under it. */
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    /** Whether the film drips down the east side of the block under it. */
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    /** Whether the film drips down the south side of the block under it. */
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    /** Whether the film drips down the west side of the block under it. */
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    private static final Map<Direction, BooleanProperty> DRIPS = Map.of(Direction.NORTH, NORTH,
            Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST);
    /** The film's thickness, a sixteenth of a block. */
    private static final double FILM_HEIGHT = 1.0 / 16;
    private static final VoxelShape FILM = Shapes.box(0, 0, 0, 1, FILM_HEIGHT, 1);

    /**
     * Creates the slime coat block.
     *
     * @param properties the block properties
     */
    public SlimeCoatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false));
    }

    /**
     * A coat's state fitted to the cells beside it: it drips on each side
     * where no coat continues the film.
     *
     * @param state the coat's state
     * @param level the level
     * @param pos   the coat's cell
     * @return the state with each side's drip set
     */
    static BlockState fittedTo(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState fitted = state;
        for (Map.Entry<Direction, BooleanProperty> side : DRIPS.entrySet()) {
            fitted = fitted.setValue(side.getValue(), dripsBeside(level.getBlockState(pos.relative(side.getKey()))));
        }
        return fitted;
    }

    /**
     * Whether the film drips on a side: its edge runs out there, the cell
     * beside holding no coat.
     *
     * @param beside the state of the cell beside the coat
     * @return true where the film ends on that side
     */
    static boolean dripsBeside(BlockState beside) {
        return !(beside.getBlock() instanceof SlimeCoatBlock);
    }

    /**
     * Whether a cell can hold a coat: the block under it has a sturdy top.
     *
     * @param level the level
     * @param pos   the cell
     * @return true where a coat can lie
     */
    public static boolean liesOn(LevelReader level, BlockPos pos) {
        BlockPos under = pos.below();
        return level.getBlockState(under).isFaceSturdy(level, under, Direction.UP);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return FILM;
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return FILM;
    }

    @Override
    protected boolean canSurvive(@NonNull BlockState state, @NonNull LevelReader level, @NonNull BlockPos pos) {
        return liesOn(level, pos);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        return fittedTo(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    /**
     * Fits a coat laid straight into its cell, as a stream's coat is, to the
     * film beside it.
     */
    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        BlockState fitted = fittedTo(state, level, pos);
        if (fitted != state) {
            level.setBlock(pos, fitted, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected @NonNull BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess ticks, @NonNull BlockPos pos,
                                              @NonNull Direction direction, @NonNull BlockPos neighborPos,
                                              @NonNull BlockState neighborState, @NonNull RandomSource random) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        BooleanProperty drip = DRIPS.get(direction);
        return drip == null ? state : state.setValue(drip, dripsBeside(neighborState));
    }
}
