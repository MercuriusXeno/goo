package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.block.ability.SlimeFlow.Side;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Weird's slime: a cell holding one to eight coat layers of glossy green
 * slime, drawn as height like snow layers, laid on a block's top with the
 * block under it unchanged and showing through, drips running down that
 * block's sides at the slime's open edges. Each deposit adds a layer, and a
 * cell taller than a side neighbour pours a layer a tick into it, and off a
 * ledge downward, until level. It stands until something destroys it, then
 * drops the weird goo its layers cost. It is Bounce's pad too: what lands
 * on it at any height takes no fall damage and bounces, ramping while jump
 * is held.
 * coat-is-a-green-slime-film-over-the-blocks-own-texture
 * slime-is-a-layered-cell-that-levels-out
 * breaking-slime-drops-weird-goo-worth-its-layers
 * bounce-ramps-jump-height-while-jump-is-held
 */
public class SlimeCoatBlock extends Block {

    /** Whether the slime drips down the north side of the block under it. */
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    /** Whether the slime drips down the east side of the block under it. */
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    /** Whether the slime drips down the south side of the block under it. */
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    /** Whether the slime drips down the west side of the block under it. */
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    /** The coat layers the cell holds, each an eighth of a block of slime. */
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    /** The most coat layers one cell holds: a whole block of slime. */
    public static final int MAX_LAYERS = 8;
    /**
     * The weird goo one coat layer costs and a broken cell gives back per
     * layer, in mb; Coat's and Bounce's costs in their JSONs equal the
     * layers they lay times this.
     * breaking-slime-drops-weird-goo-worth-its-layers
     */
    public static final int MB_PER_LAYER = 12;
    /** Ticks between one pour and the next, as a fluid spreads. */
    static final int FLOW_TICKS = 2;
    private static final Map<Direction, BooleanProperty> DRIPS = Map.of(Direction.NORTH, NORTH,
            Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST);
    private static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST);
    /** Each layer count's box, indexed by the count. */
    private static final VoxelShape[] LAYER_BOXES = new VoxelShape[MAX_LAYERS + 1];

    static {
        for (int layers = 1; layers <= MAX_LAYERS; layers++) {
            LAYER_BOXES[layers] = layerBox(layers);
        }
    }

    /**
     * Creates the slime coat block.
     *
     * @param properties the block properties
     */
    public SlimeCoatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false).setValue(LAYERS, 1));
    }

    /**
     * The box a cell of slime fills: a full block across, its layers'
     * eighths of a block high.
     *
     * @param layers the coat layers the cell holds
     * @return the box
     */
    static VoxelShape layerBox(int layers) {
        return Shapes.box(0, 0, 0, 1, (double) layers / MAX_LAYERS, 1);
    }

    /**
     * The weird goo a broken cell gives back: what its layers cost.
     * breaking-slime-drops-weird-goo-worth-its-layers
     *
     * @param layers the coat layers the cell held
     * @return the goo in mb
     */
    static int reclaimed(int layers) {
        return layers * MB_PER_LAYER;
    }

    /**
     * Gives back the weird goo the cell's layers cost, as the glow crystal
     * gives back its goo, whatever breaks the cell.
     * breaking-slime-drops-weird-goo-worth-its-layers
     */
    @Override
    protected @NonNull List<ItemStack> getDrops(@NonNull BlockState state, LootParams.@NonNull Builder builder) {
        return List.of(GooStacks.createForOutput(GooTypes.WEIRD, reclaimed(state.getValue(LAYERS))));
    }

    /**
     * The coat layers a cell holds.
     *
     * @param state the cell's state
     * @return its layers, 0 for anything but slime
     */
    public static int layersOf(BlockState state) {
        return state.getBlock() instanceof SlimeCoatBlock ? state.getValue(LAYERS) : 0;
    }

    /**
     * What a cell becomes when one more coat layer lands in it: air takes a
     * layer of slime, slime one layer more up to a whole block.
     *
     * @param standing the cell's state
     * @param slime    the slime block
     * @return the thickened state, empty where the cell can take no layer
     */
    public static Optional<BlockState> thickened(BlockState standing, Block slime) {
        if (standing.isAir()) {
            return Optional.of(slime.defaultBlockState());
        }
        int layers = layersOf(standing);
        return layers > 0 && layers < MAX_LAYERS ? Optional.of(standing.setValue(LAYERS, layers + 1))
                : Optional.empty();
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
     * Whether the coat hides its face toward a neighbour: toward another
     * coat, so neighbouring coats read as one film with no seam between, as
     * slime blocks do.
     *
     * @param neighbour the state of the cell beside the face
     * @return true where the face is not drawn
     */
    static boolean hidesFaceToward(BlockState neighbour) {
        return neighbour.getBlock() instanceof SlimeCoatBlock;
    }

    /**
     * Hides a face toward slime standing at least as tall, so a taller cell
     * still draws the side it shows above a lower neighbour.
     */
    @Override
    protected boolean skipRendering(@NonNull BlockState state, @NonNull BlockState neighbour,
                                    @NonNull Direction direction) {
        boolean covered = direction.getAxis().isVertical() || layersOf(neighbour) >= layersOf(state);
        return hidesFaceToward(neighbour) && covered || super.skipRendering(state, neighbour, direction);
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
        builder.add(NORTH, EAST, SOUTH, WEST, LAYERS);
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return LAYER_BOXES[state.getValue(LAYERS)];
    }

    @Override
    protected @NonNull VoxelShape getCollisionShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                                    @NonNull BlockPos pos, @NonNull CollisionContext ctx) {
        return LAYER_BOXES[state.getValue(LAYERS)];
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
     * film beside it, and starts it flowing.
     * slime-is-a-layered-cell-that-levels-out
     */
    @Override
    protected void onPlace(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                           @NonNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!canSurvive(state, level, pos)) {
            // a blob landing beside a wall finds no top to coat
            level.removeBlock(pos, false);
            return;
        }
        BlockState fitted = fittedTo(state, level, pos);
        if (fitted != state) {
            level.setBlock(pos, fitted, Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, FLOW_TICKS);
    }

    /**
     * Pours one layer into the side the flow rule picks, which places the
     * cell anew and so schedules the next pour, until the cell stands level.
     * slime-is-a-layered-cell-that-levels-out
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        List<Side> sides = SIDES.stream().map(side -> sideOf(level, pos.relative(side), side)).toList();
        SlimeFlow.pour(state.getValue(LAYERS), sides).ifPresent(side -> pourToward(level, pos, state, side));
    }

    /**
     * What a side neighbour's cell can do with a poured layer.
     *
     * @param level the level
     * @param cell  the neighbour's cell
     * @param side  the side it stands on
     * @return the side, its layers and its footing
     */
    private static Side sideOf(LevelReader level, BlockPos cell, Direction side) {
        BlockState standing = level.getBlockState(cell);
        if (standing.getBlock() instanceof SlimeCoatBlock) {
            return Side.holds(side, layersOf(standing));
        }
        if (!standing.isAir()) {
            return Side.blocked(side);
        }
        BlockState under = level.getBlockState(cell.below());
        if (under.isAir() || unfilledSlime(under)) {
            return Side.ledge(side);
        }
        return liesOn(level, cell) ? Side.holds(side, 0) : Side.blocked(side);
    }

    private static boolean unfilledSlime(BlockState state) {
        int layers = layersOf(state);
        return layers > 0 && layers < MAX_LAYERS;
    }

    /**
     * Moves one layer from a cell toward a side: into the neighbour where it
     * holds slime, or down off the ledge to the first top below it.
     *
     * @param level the level
     * @param pos   the pouring cell
     * @param state the pouring cell's state
     * @param side  the side it pours toward
     */
    private void pourToward(ServerLevel level, BlockPos pos, BlockState state, Direction side) {
        BlockPos toward = pos.relative(side);
        BlockState beside = level.getBlockState(toward);
        Optional<BlockPos> landing = beside.isAir() && !liesOn(level, toward) ? landingBelow(level, toward)
                : Optional.of(toward);
        landing.flatMap(cell -> thickened(level.getBlockState(cell), this).map(into -> {
            level.setBlock(cell, into, Block.UPDATE_ALL);
            return cell;
        })).ifPresent(cell -> level.setBlock(pos, state.setValue(LAYERS, state.getValue(LAYERS) - 1),
                Block.UPDATE_ALL));
    }

    /**
     * Where a layer falling through a ledge lands: in the first unfilled
     * slime below it, or in the last open cell over the first sturdy top.
     *
     * @param level the level
     * @param ledge the open cell the layer falls through
     * @return the landing cell, empty where it finds no top
     */
    private static Optional<BlockPos> landingBelow(LevelReader level, BlockPos ledge) {
        BlockPos open = ledge;
        BlockPos below = ledge.below();
        while (below.getY() >= level.getMinY() && level.getBlockState(below).isAir()) {
            open = below;
            below = below.below();
        }
        if (below.getY() >= level.getMinY() && unfilledSlime(level.getBlockState(below))) {
            return Optional.of(below);
        }
        return liesOn(level, open) ? Optional.of(open) : Optional.empty();
    }

    /**
     * Breaks whatever fall lands on the coat.
     * bounce-ramps-jump-height-while-jump-is-held
     */
    @Override
    public void fallOn(@NonNull Level level, @NonNull BlockState state, @NonNull BlockPos pos,
                       @NonNull Entity entity, double fallDistance) {
        entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
    }

    /**
     * Bounces what lands on the coat, ramping while jump is held.
     * bounce-ramps-jump-height-while-jump-is-held
     */
    @Override
    public void updateEntityMovementAfterFallOn(@NonNull BlockGetter level, @NonNull Entity entity) {
        if (entity.getDeltaMovement().y < 0) {
            SlimeCoatBounce.bounce(entity);
        } else {
            super.updateEntityMovementAfterFallOn(level, entity);
        }
    }

    /**
     * Drops the coat where its top is gone, refits its drips, and wakes the
     * flow, since a changed neighbour may open a pour.
     * slime-is-a-layered-cell-that-levels-out
     */
    @Override
    protected @NonNull BlockState updateShape(@NonNull BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess ticks, @NonNull BlockPos pos,
                                              @NonNull Direction direction, @NonNull BlockPos neighborPos,
                                              @NonNull BlockState neighborState, @NonNull RandomSource random) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        ticks.scheduleTick(pos, this, FLOW_TICKS);
        BooleanProperty drip = DRIPS.get(direction);
        return drip == null ? state : state.setValue(drip, dripsBeside(neighborState));
    }
}
