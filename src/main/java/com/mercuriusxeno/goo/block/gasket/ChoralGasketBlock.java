package com.mercuriusxeno.goo.block.gasket;

import com.mercuriusxeno.goo.block.BlockEntityTicks;
import com.mercuriusxeno.goo.block.GooMachineBlock;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * World-placeable choral gasket block. Sits on top of a solid surface,
 * holds fluid (goo or vanilla), and transmits it through the gasket
 * network. When waterlogged, continuously self-fills with water.
 */
public class ChoralGasketBlock extends GooMachineBlock implements SimpleWaterloggedBlock {

    /**
     * Whether the block is waterlogged.
     */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final MapCodec<ChoralGasketBlock> CODEC = simpleCodec(ChoralGasketBlock::new);
    /**
     * 4x4x1 pixel shape centered at the bottom of the blockspace.
     */
    private static final VoxelShape SHAPE = box(6, 0, 6, 10, 1, 10);

    /**
     * Creates a choral gasket block.
     *
     * @param properties the block properties
     */
    public ChoralGasketBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected @NonNull MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected @NonNull RenderShape getRenderShape(@NonNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected @NonNull VoxelShape getShape(@NonNull BlockState state, @NonNull BlockGetter level,
                                           @NonNull BlockPos pos, @NonNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.@NonNull Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(@NonNull BlockPlaceContext context) {
        BlockPos placePos = context.getClickedPos().relative(context.getClickedFace());
        FluidState fluid = context.getLevel().getFluidState(placePos);
        boolean waterlogged = fluid.is(Fluids.WATER);
        return defaultBlockState()
                .setValue(WATERLOGGED, waterlogged);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected @NonNull BlockState updateShape(BlockState state, @NonNull LevelReader level,
                                              @NonNull ScheduledTickAccess ticks, @NonNull BlockPos pos,
                                              @NonNull Direction direction, @NonNull BlockPos neighborPos,
                                              @NonNull BlockState neighborState, @NonNull RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, @NonNull BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NonNull BlockPos pos, @NonNull BlockState state) {
        return new ChoralGasketBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityTicks<ChoralGasketBlockEntity> ticks() {
        return BlockEntityTicks.onServer(GooBlockEntities.CHORAL_GASKET, ChoralGasketBlockEntity::serverTick);
    }

    /**
     * Fluid containers (buckets) interact with the gasket's tank.
     *
     * @param stack     the held item
     * @param state     the block state
     * @param level     the level
     * @param pos       the block position
     * @param player    the player
     * @param hand      the hand used
     * @param hitResult the hit result
     * @return the interaction result
     */
    @Override
    protected @NonNull InteractionResult useItemOn(
            @NonNull ItemStack stack, @NonNull BlockState state,
            Level level, @NonNull BlockPos pos, @NonNull Player player,
            @NonNull InteractionHand hand, @NonNull BlockHitResult hitResult) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
}
